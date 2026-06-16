package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.response.SupportFileUploadResponse;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import com.chunbaetour.domain.cs.storage.SupportFileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 상담 채팅 파일/이미지 업로드 서비스 (KAN-310, ChatFileService 패턴 재사용).
 * 파일 유효성 검사 + SupportFileStorage 위임 구조 — 운영=S3SupportFileStorage(@Profile prod), 그 외=LocalDiskSupportFileStorage.
 * 반환값은 접근 URL이 아니라 <b>객체 키</b>다(조회/브로드캐스트 시 presigned GET으로 변환).
 *
 * [트랜잭션 경계]
 * uploadFile()은 DB 변경 없이 조회(권한 확인) + 외부 저장소 업로드만 수행하므로 @Transactional 불필요.
 * 메시지(SupportMessage) 영속화는 별도 STOMP 전송(SupportMessageService.sendMessage)이 담당 — 업로드는 키만 반환한다
 * (고아 객체 문제는 키 미저장으로 회피, 채팅 패턴 동일).
 *
 * [보안 경계]
 * MultipartFile.getContentType()은 브라우저 선언값(user-supplied)이라 위장 가능.
 * declared content-type 화이트리스트 + magic-byte 검증(이미지=경량 시그니처, 문서=Apache Tika)을 함께 적용해
 * 위장 업로드를 차단한다(09_정책_결정_기록 — 채팅 파일/이미지 업로드 정책).
 */
@Service
@RequiredArgsConstructor
public class SupportFileService {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L; // 5MB
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024L; // 10MB

    /** "image/jpg"는 비표준 MIME — 브라우저는 .jpg/.jpeg 모두 image/jpeg로 전송하므로 포함 불필요 */
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    /** 압축파일(zip 등) 전면 제외 — 내부 콘텐츠 표면 검사 불가, 악성코드 우회 경로로 자주 악용됨 */
    private static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/x-hwp");

    /**
     * OOXML(docx/xlsx/pptx) 컨테이너 포맷군 — Tika.detect(InputStream)이 [Content_Types].xml 존재로
     * "OOXML 패키지"는 식별하지만, 파일명 확장자 힌트 없이는 docx/xlsx/pptx 서브타입까지는 구분하지 못하고
     * {@link #TIKA_OOXML_GENERIC}으로만 반환한다(직접 검증, TikaCheck). declared가 이 셋 중 하나면
     * detected==TIKA_OOXML_GENERIC을 허용 — "OOXML 컨테이너군 일치" 검사로, zip/PDF/스크립트 등
     * 비-OOXML 위장(=[Content_Types].xml 없음, application/zip으로 감지)은 여전히 차단된다.
     */
    private static final Set<String> OOXML_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private static final String TIKA_OOXML_GENERIC = "application/x-tika-ooxml";

    /**
     * HWP 5.0(현재 표준, OLE2 복합 문서 포맷)은 Tika mimetypes상 application/x-hwp-v5로
     * "sub-class-of application/x-tika-msoffice"일 뿐 별도 magic이 없어, 실제 감지 결과는
     * application/x-tika-msoffice(OLE2 제너릭)로 나온다(직접 검증, TikaCheck). declared=application/x-hwp일 때
     * OLE2 컨테이너군 일치 선검증 후 isHwpV5()로 "FileHeader" 스트림 시그니처까지 확인한다 —
     * OLE2이지만 "FileHeader" 없는 위장 .doc/.xls/.ppt는 isHwpV5()에서 차단된다.
     * application/x-hwp(HWP v5 이전, 문자열 시그니처 "HWP Document File V")는 Tika가 직접 감지 가능 — 그대로 매칭.
     */
    private static final Set<String> OLE2_CONTAINER_TYPES = Set.of(
            "application/x-hwp", "application/x-tika-msoffice");

    /** HWP v5 OLE2 컨테이너의 "FileHeader" 스트림 시작 바이트(32바이트 시그니처 필드의 앞부분) */
    private static final byte[] HWP_V5_FILEHEADER_SIGNATURE =
            "HWP Document File".getBytes(StandardCharsets.US_ASCII);

    private final SupportRoomRepository supportRoomRepository;
    private final SupportFileStorage supportFileStorage;
    private final Tika tika = new Tika();

    /**
     * 상담 채팅 파일/이미지 업로드.
     * SupportMessageService.sendMessage와 동일한 발신 권한 검증(CLOSED 차단, ADMIN/CUSTOMER 분기) 후
     * 파일 유효성 검사(크기·content-type·magic-byte)를 거쳐 supportFileStorage에 위임하고 객체 키를 반환한다.
     * 파일명은 originalFilename을 쓰지 않고 SupportFileKeys가 UUID로 재생성한다(path traversal 차단).
     */
    public SupportFileUploadResponse uploadFile(Long userId, Long supportRoomId, boolean isAdmin, MultipartFile file) {
        SupportRoom room = supportRoomRepository.findById(supportRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND));

        // 종료된 방은 업로드 차단 — 업로드만 되고 메시지 전송이 막혀 S3 고아 객체가 확정 발생하는 것을 사전 차단(채팅 패턴 동일).
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED);
        }

        if (isAdmin) {
            // 배정된 ADMIN만 업로드 가능 — IN_PROGRESS 상태 + adminId 일치
            if (room.getStatus() != SupportRoomStatus.IN_PROGRESS || !userId.equals(room.getAdminId())) {
                throw new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
            }
        } else {
            // USER·MERCHANT는 본인 방에만 업로드 가능
            if (!room.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
            }
        }

        validateFile(file);

        String objectKey = supportFileStorage.upload(supportRoomId, file);
        return new SupportFileUploadResponse(objectKey, sanitizeFileName(file.getOriginalFilename()), file.getSize(), file.getContentType());
    }

    private void validateFile(MultipartFile file) {
        // Spring @RequestParam 누락 시 서비스 도달 전 400 반환 — null은 실제 발생하지 않음
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.SUPPORT_FILE_EMPTY);
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BusinessException(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
        }

        if (IMAGE_CONTENT_TYPES.contains(contentType)) {
            if (file.getSize() > MAX_IMAGE_SIZE) {
                throw new BusinessException(ErrorCode.SUPPORT_IMAGE_FILE_TOO_LARGE);
            }
            if (!isAllowedImageSignature(file, contentType)) {
                throw new BusinessException(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
            }
        } else if (DOCUMENT_CONTENT_TYPES.contains(contentType)) {
            if (file.getSize() > MAX_DOCUMENT_SIZE) {
                throw new BusinessException(ErrorCode.SUPPORT_FILE_TOO_LARGE);
            }
            if (!isAllowedDocumentSignature(file, contentType)) {
                throw new BusinessException(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
            }
        } else {
            throw new BusinessException(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
        }
    }

    /**
     * 매직바이트가 declared content-type과 <b>같은 포맷</b>인지 검사.
     * "허용 포맷 중 하나"가 아니라 "선언값과 일치"를 봐야 declared=png/실제=jpeg 같은 불일치를 막는다(채팅 패턴 동일).
     */
    private static boolean isAllowedImageSignature(MultipartFile file, String contentType) {
        byte[] h;
        try {
            h = file.getInputStream().readNBytes(12);
        } catch (IOException e) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> isJpeg(h);
            case "image/png" -> isPng(h);
            case "image/webp" -> isWebp(h);
            default -> false;
        };
    }

    /**
     * 문서 magic-byte 검증 — Apache Tika로 실제 포맷을 감지해 declared content-type과 비교.
     * 파일명(originalFilename)은 위장 가능하므로 detect에 전달하지 않고 바이트 시그니처만으로 판정한다.
     */
    private boolean isAllowedDocumentSignature(MultipartFile file, String contentType) {
        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = tika.detect(in);
        } catch (IOException e) {
            return false;
        }
        if (contentType.equals(detected)) {
            return true;
        }
        // HWP v5: OLE2 컨테이너군 일치 선검증 후 POIFS로 "FileHeader" 시그니처 확인 — 위장 .doc/.xls/.ppt 차단
        if (contentType.equals("application/x-hwp") && OLE2_CONTAINER_TYPES.contains(detected)) {
            return isHwpV5(file);
        }
        // docx/xlsx/pptx는 OOXML 컨테이너군 일치로 판정(OOXML_CONTENT_TYPES 주석 참고)
        return OOXML_CONTENT_TYPES.contains(contentType) && TIKA_OOXML_GENERIC.equals(detected);
    }

    // JPEG: FF D8 FF
    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    // PNG: 89 50 4E 47 0D 0A 1A 0A
    private static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
    }

    // WebP: 'R''I''F''F' [4바이트 크기] 'W''E''B''P'
    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    /**
     * OLE2 컨테이너의 "FileHeader" 스트림을 열어 HWP v5 시그니처("HWP Document File")로 시작하는지 확인.
     * 위장된 .doc/.xls/.ppt는 "FileHeader" 스트림이 없거나(POI가 FileNotFoundException) 시그니처가 달라 차단된다.
     */
    private static boolean isHwpV5(MultipartFile file) {
        try (InputStream in = file.getInputStream();
                POIFSFileSystem fs = new POIFSFileSystem(in)) {
            Entry entry = fs.getRoot().getEntry("FileHeader");
            if (!(entry instanceof DocumentEntry documentEntry)) {
                return false;
            }
            try (DocumentInputStream header = new DocumentInputStream(documentEntry)) {
                byte[] signature = header.readNBytes(HWP_V5_FILEHEADER_SIGNATURE.length);
                return Arrays.equals(signature, HWP_V5_FILEHEADER_SIGNATURE);
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** 메시지 표시용 파일명 — `\`와 `/` 모두 경로 구분자로 정규화해 제거(Windows 경로 위장 방어). 저장 키에는 사용되지 않음(UUID). */
    private static String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        // Paths.get()은 OS 기본 구분자만 인식 — Linux에서 Windows 경로(`C:\...`) 업로드 시 `\` 미제거
        String normalized = originalFilename.replace('\\', '/');
        return Paths.get(normalized).getFileName().toString();
    }
}
