package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import com.chunbaetour.domain.cs.storage.SupportFileStorage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SupportFileService 단위 테스트 — 발신 권한(CLOSED/ADMIN/CUSTOMER) 검증 + 크기·magic-byte 검증 + storage 위임 (KAN-309 CS).
 */
@ExtendWith(MockitoExtension.class)
class SupportFileServiceTest {

    @Mock
    private SupportRoomRepository supportRoomRepository;

    @Mock
    private SupportFileStorage supportFileStorage;

    @InjectMocks
    private SupportFileService supportFileService;

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long ROOM_ID = 10L;

    private SupportRoom room() {
        return SupportRoom.builder().userId(CUSTOMER_ID).build();
    }

    private SupportRoom assignedRoom() {
        SupportRoom room = room();
        room.assignAdmin(ADMIN_ID);
        return room;
    }

    private SupportRoom closedRoom() {
        SupportRoom room = room();
        ReflectionTestUtils.setField(room, "status", SupportRoomStatus.CLOSED);
        return room;
    }

    /** JPEG 매직바이트(FF D8 FF) 더미. */
    private static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    /** PNG 매직바이트. */
    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    /** WebP 매직바이트(RIFF....WEBP). */
    private static byte[] webpBytes() {
        return new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    }

    /** OLE2 복합 문서 시그니처 — HWP 5.0(현재 표준)은 이 포맷으로 저장됨. */
    private static byte[] ole2Bytes() {
        return new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    /** HWP v5 이전 포맷 문자열 시그니처("HWP Document File V", offset 0). */
    private static byte[] oldHwpBytes() {
        return "HWP Document File V3.00    ".getBytes();
    }

    /**
     * 최소 유효 OOXML zip — [Content_Types].xml에 mainEntry의 ContentType을 선언해 Tika가
     * OOXML 패키지(application/x-tika-ooxml)로 인식하게 한다.
     */
    private static byte[] ooxmlBytes(String mainEntryPath, String mainEntryContentType) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/" + mainEntryPath + "\" ContentType=\"" + mainEntryContentType + "\"/>"
                        + "</Types>";
                zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zos.write(contentTypesXml.getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry(mainEntryPath));
                zos.write("<root/>".getBytes());
                zos.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 압축파일(zip)이지만 [Content_Types].xml이 없는 일반 zip — OOXML 패키지로 인식되지 않음. */
    private static byte[] plainZipBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                zos.putNextEntry(new ZipEntry("readme.txt"));
                zos.write("hello".getBytes());
                zos.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PPTX_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    // ===== 사전 조건 (방 상태 / 발신 권한) =====

    @Test
    @DisplayName("업로드 — 존재하지 않는 상담방 → SUPPORT_ROOM_NOT_FOUND")
    void uploadFile_roomNotFound_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("업로드 — CLOSED 상담방 → SUPPORT_ROOM_ALREADY_CLOSED (고아 객체 사전 차단)")
    void uploadFile_closedRoom_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(closedRoom()));

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED);
    }

    @Test
    @DisplayName("업로드 — CUSTOMER가 타인 상담방에 업로드 → SUPPORT_ROOM_FORBIDDEN")
    void uploadFile_customerNotOwner_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(999L, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
    }

    @Test
    @DisplayName("업로드 — ADMIN이 WAITING(미배정) 상담방에 업로드 → SUPPORT_ROOM_FORBIDDEN")
    void uploadFile_adminOnWaitingRoom_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(ADMIN_ID, ROOM_ID, true, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
    }

    @Test
    @DisplayName("업로드 — 배정되지 않은 ADMIN → SUPPORT_ROOM_FORBIDDEN")
    void uploadFile_adminNotAssigned_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(assignedRoom()));

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(999L, ROOM_ID, true, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
    }

    // ===== 공통 검증 (빈 파일 / 미지원 타입) =====

    @Test
    @DisplayName("업로드 — 빈 파일 → SUPPORT_FILE_EMPTY")
    void uploadFile_emptyFile_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_FILE_EMPTY);
    }

    @Test
    @DisplayName("업로드 — 허용되지 않은 content-type(application/zip) → SUPPORT_FILE_TYPE_UNSUPPORTED")
    void uploadFile_unsupportedContentType_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        MockMultipartFile file = new MockMultipartFile("file", "a.zip", "application/zip", plainZipBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
    }

    // ===== 이미지 =====

    @Test
    @DisplayName("업로드 — 이미지 5MB 초과 → SUPPORT_IMAGE_FILE_TOO_LARGE")
    void uploadFile_imageTooLarge_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        byte[] big = new byte[6 * 1024 * 1024];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", big);

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_IMAGE_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("업로드 — declared image/jpeg인데 실제 바이트가 위장(MZ) → SUPPORT_FILE_TYPE_UNSUPPORTED")
    void uploadFile_imageMagicByteMismatch_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        byte[] fake = new byte[12];
        fake[0] = 'M';
        fake[1] = 'Z';
        MockMultipartFile file = new MockMultipartFile("file", "evil.jpg", "image/jpeg", fake);

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("업로드 — declared image/png인데 실제 바이트는 JPEG → SUPPORT_FILE_TYPE_UNSUPPORTED")
    void uploadFile_declaredPngButJpegBytes_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", jpegBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("업로드 — 유효한 JPEG/PNG/WebP → storage 위임 후 SupportFileUploadResponse 반환")
    void uploadFile_validImages_delegatesToStorage() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.jpg");

        MockMultipartFile jpeg = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        var response = supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, jpeg);

        assertThat(response.fileUrl()).isEqualTo("support-rooms/10/uuid.jpg");
        assertThat(response.fileName()).isEqualTo("photo.jpg");
        assertThat(response.fileSize()).isEqualTo(jpegBytes().length);
        assertThat(response.contentType()).isEqualTo("image/jpeg");

        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.png");
        MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png", pngBytes());
        assertThat(supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, png).fileUrl()).isEqualTo("support-rooms/10/uuid.png");

        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.webp");
        MockMultipartFile webp = new MockMultipartFile("file", "photo.webp", "image/webp", webpBytes());
        assertThat(supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, webp).fileUrl()).isEqualTo("support-rooms/10/uuid.webp");
    }

    @Test
    @DisplayName("업로드 — 배정된 ADMIN(IN_PROGRESS) → storage 위임 후 SupportFileUploadResponse 반환")
    void uploadFile_assignedAdmin_delegatesToStorage() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(assignedRoom()));
        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.jpg");

        MockMultipartFile jpeg = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        var response = supportFileService.uploadFile(ADMIN_ID, ROOM_ID, true, jpeg);

        assertThat(response.fileUrl()).isEqualTo("support-rooms/10/uuid.jpg");
    }

    // ===== 문서 =====

    @Test
    @DisplayName("업로드 — 문서 10MB 초과 → SUPPORT_FILE_TOO_LARGE")
    void uploadFile_documentTooLarge_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        byte[] big = new byte[11 * 1024 * 1024];
        System.arraycopy("%PDF-1.4\n".getBytes(), 0, big, 0, 9);
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("업로드 — declared application/pdf인데 실제 바이트는 이미지(JPEG) → SUPPORT_FILE_TYPE_UNSUPPORTED")
    void uploadFile_documentMagicByteMismatch_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", jpegBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("업로드 — 유효한 PDF → storage 위임 후 객체 키 반환")
    void uploadFile_validPdf_delegatesToStorage() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.pdf");

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
        var response = supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file);

        assertThat(response.fileUrl()).isEqualTo("support-rooms/10/uuid.pdf");
        assertThat(response.fileName()).isEqualTo("doc.pdf");
    }

    @Test
    @DisplayName("업로드 — 유효한 DOCX/XLSX/PPTX(OOXML 패키지) → storage 위임 후 객체 키 반환")
    void uploadFile_validOoxmlDocuments_delegatesToStorage() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.docx");
        MockMultipartFile docx = new MockMultipartFile("file", "doc.docx", DOCX_TYPE,
                ooxmlBytes("word/document.xml", DOCX_TYPE + ".main+xml"));
        assertThat(supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, docx).fileUrl()).isEqualTo("support-rooms/10/uuid.docx");

        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.xlsx");
        MockMultipartFile xlsx = new MockMultipartFile("file", "doc.xlsx", XLSX_TYPE,
                ooxmlBytes("xl/workbook.xml", XLSX_TYPE + ".main+xml"));
        assertThat(supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, xlsx).fileUrl()).isEqualTo("support-rooms/10/uuid.xlsx");

        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.pptx");
        MockMultipartFile pptx = new MockMultipartFile("file", "doc.pptx", PPTX_TYPE,
                ooxmlBytes("ppt/presentation.xml", PPTX_TYPE + ".main+xml"));
        assertThat(supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, pptx).fileUrl()).isEqualTo("support-rooms/10/uuid.pptx");
    }

    @Test
    @DisplayName("업로드 — declared docx인데 실제는 [Content_Types].xml 없는 일반 zip → SUPPORT_FILE_TYPE_UNSUPPORTED")
    void uploadFile_plainZipDeclaredAsDocx_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        MockMultipartFile file = new MockMultipartFile("file", "fake.docx", DOCX_TYPE, plainZipBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("업로드 — 유효한 HWP(구버전 문자열 시그니처/v5 OLE2) → storage 위임 후 객체 키 반환")
    void uploadFile_validHwp_delegatesToStorage() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        // 구버전 HWP — "HWP Document File V" 문자열 시그니처(Tika가 application/x-hwp로 직접 감지)
        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid-old.hwp");
        MockMultipartFile oldHwp = new MockMultipartFile("file", "old.hwp", "application/x-hwp", oldHwpBytes());
        assertThat(supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, oldHwp).fileUrl()).isEqualTo("support-rooms/10/uuid-old.hwp");

        // HWP 5.0(현재 표준) — OLE2 복합 문서(Tika가 application/x-tika-msoffice로 감지, OLE2 컨테이너군 일치로 허용)
        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid-v5.hwp");
        MockMultipartFile hwpV5 = new MockMultipartFile("file", "v5.hwp", "application/x-hwp", ole2Bytes());
        assertThat(supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, hwpV5).fileUrl()).isEqualTo("support-rooms/10/uuid-v5.hwp");
    }

    @Test
    @DisplayName("업로드 — declared application/x-hwp인데 실제 바이트는 PDF → SUPPORT_FILE_TYPE_UNSUPPORTED")
    void uploadFile_hwpMagicByteMismatch_throws() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));

        MockMultipartFile file = new MockMultipartFile("file", "fake.hwp", "application/x-hwp", "%PDF-1.4\n".getBytes());

        assertThatThrownBy(() -> supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
    }

    // ===== 파일명 sanitize =====

    @Test
    @DisplayName("업로드 — originalFilename이 경로를 포함해도 파일명만 추출")
    void uploadFile_sanitizesFileNameWithPath() {
        given(supportRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(supportFileStorage.upload(eq(ROOM_ID), any())).willReturn("support-rooms/10/uuid.jpg");

        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.jpg", "image/jpeg", jpegBytes());

        var response = supportFileService.uploadFile(CUSTOMER_ID, ROOM_ID, false, file);

        assertThat(response.fileName()).isEqualTo("passwd.jpg");
    }
}
