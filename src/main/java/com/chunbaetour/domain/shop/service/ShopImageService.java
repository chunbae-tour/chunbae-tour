package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.response.ShopImageItemResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.storage.ShopImageKeys;
import com.chunbaetour.domain.shop.storage.ShopImageStorage;
import com.chunbaetour.domain.shop.type.ShopImageType;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 가게 사진 서비스 (KAN-188 E10 → KAN-315 ADR-003).
 *
 * <p>ADR-003으로 저장 모델을 {@code Shop.imageUrls} 단일 JSON blob → {@code shop_images} 테이블(행 단위
 * 용도/순서/대표)로 전환하고, 업로드를 <b>1단계화</b>한다 — 업로드 즉시 행을 생성해 "업로드 후 자동반영" 갭을 해소한다
 * (기존 2단계: 업로드로 키만 반환 + 별도 PATCH 저장 → 폐기).
 *
 * <p>[용도 정책]
 * <ul>
 *   <li>PROFILE: 가게당 1장. 신규 업로드 시 기존 PROFILE 행을 교체(MySQL 부분 unique index 미지원 → 앱 레벨 보장).</li>
 *   <li>GALLERY: 다중. {@code sort_order}를 max+1로 부여해 업로드 순서대로 정렬.</li>
 * </ul>
 *
 * <p>[보안 경계] {@code MultipartFile.getContentType()}은 user-supplied라 .exe를 image/jpeg로 위장 가능 →
 * declared content-type 화이트리스트 + magic-byte 시그니처(JPEG/PNG/WebP)를 함께 검증한다(경량, Tika 미사용).
 *
 * <p>[조회/삭제] 조회는 저장 키를 기존 presign 파이프라인({@code ShopImageStorage.presignedGetUrl})으로 변환하며,
 * presign 실패는 해당 사진만 건너뛰어(graceful degrade) 한 장 때문에 목록 전체가 깨지지 않게 한다. 삭제는 행 제거 +
 * S3 객체 best-effort 삭제(잔존 고아는 후속 cleanup 스케줄러가 회수 — KAN-298 패턴).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    /** "image/jpg"는 비표준 MIME — 브라우저는 .jpg/.jpeg 모두 image/jpeg로 전송하므로 포함 불필요 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final ShopRepository shopRepository;
    private final ShopImageRepository shopImageRepository;
    private final ShopImageStorage imageStorage;

    /**
     * 가게 사진 업로드(1단계). 소유권·ACTIVE 검증 + 파일 유효성 검사 후 S3 업로드 → {@code shop_images} 행 즉시 생성.
     * PROFILE은 기존 행을 교체(행 삭제 + 옛 객체 best-effort 삭제)하고, GALLERY는 sort_order를 이어붙인다.
     */
    @Transactional
    public ShopImageItemResponse uploadImage(Long userId, Long shopId, ShopImageType type, MultipartFile file) {
        // type 누락/오류는 컨트롤러 단(@RequestParam 변환)에서 400 처리 — 여기 도달 시 null만 방어
        if (type == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 소유권 + ACTIVE 확인 — 타인 가게 업로드 차단(IDOR), 비활성 가게 차단(고아 객체 예방)
        Shop shop = getActiveShop(userId, shopId);

        // 파일 유효성 검사(크기·content-type·magic-byte)
        validateFile(file);

        // 검증 통과 후 저장소 업로드 → 객체 키 확보
        String objectKey = imageStorage.upload(shopId, file);

        ShopImage saved = (type == ShopImageType.PROFILE)
                ? replaceProfile(shop.getId(), userId, objectKey)
                : appendGallery(shop.getId(), objectKey);

        // 업로드 응답에도 presign URL을 실어 프론트가 추가 조회 없이 바로 렌더 가능(자동반영 갭 해소)
        return ShopImageItemResponse.of(saved, imageStorage.presignedGetUrl(objectKey));
    }

    /**
     * 가게 사진 목록 조회. 조회는 가게 상태 무관 허용(SUSPENDED/CLOSED에서도 상인이 확인 가능 — 공지 정책과 동일).
     * 저장 키를 presigned URL로 변환하며, presign 실패한 사진은 건너뛴다(graceful degrade).
     */
    public List<ShopImageItemResponse> getImages(Long userId, Long shopId) {
        // 소유권 검증(상태 무관) — 타 가게 목록 조회 차단
        shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        List<ShopImageItemResponse> result = new ArrayList<>();
        for (ShopImage image : shopImageRepository.findByShopIdOrderByTypeDescSortOrderAscIdAsc(shopId)) {
            // IDOR 2중 방어 — 정상 경로면 업로드가 shops/{shopId}/ prefix로 키를 생성하므로 항상 통과.
            // 여기 걸리면 DB 직접수정/마이그레이션 신호 → presign 안 함(타 가게 객체 노출 차단).
            if (!ShopImageKeys.belongsToShop(image.getObjectKey(), shopId)) {
                log.warn("presign 건너뜀 — 소유권 불일치 키. shopId={}, imageId={}, key={}",
                        shopId, image.getId(), image.getObjectKey());
                continue;
            }
            try {
                result.add(ShopImageItemResponse.of(image, imageStorage.presignedGetUrl(image.getObjectKey())));
            } catch (BusinessException e) {
                // 외부 의존(presign) 실패만 부분 실패로 격하 — 사진 한 장 때문에 목록 전체가 503이 되지 않게(자가검증 반영).
                if (e.getErrorCode() != ErrorCode.EXTERNAL_SERVICE_ERROR) {
                    throw e;
                }
                log.warn("presign 실패로 사진 건너뜀. shopId={}, imageId={}", shopId, image.getId(), e);
            }
        }
        return result;
    }

    /**
     * 가게 사진 삭제. ACTIVE 가게만 허용. imageId + shopId 조합으로 소유권 검증(타 가게 이미지는 SHOP_IMAGE_NOT_FOUND).
     * 행 제거 후 S3 객체를 best-effort 삭제한다(실패해도 행 삭제는 유지 — 고아는 후속 cleanup 회수).
     */
    @Transactional
    public void deleteImage(Long userId, Long shopId, Long imageId) {
        Shop shop = getActiveShop(userId, shopId);

        ShopImage image = shopImageRepository.findByIdAndShopId(imageId, shop.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_IMAGE_NOT_FOUND));

        // 행 먼저 삭제(진실 원천) → 그 다음 객체 삭제. 순서 의도: DB-first라야 "행은 있는데 객체 없음"(GET 깨짐)을
        //   피한다. 역으로 객체 삭제가 타임아웃·일시장애로 실패하면 S3 고아가 남지만, best-effort로 흐름은 진행한다.
        //   고아 회수는 후속 cleanup 스케줄러가 담당(미구현, ADR-003 범위 밖 / 미구현API.md B24 추적, KAN-298 패턴 재사용).
        shopImageRepository.delete(image);
        imageStorage.delete(image.getObjectKey());
    }

    /**
     * PROFILE 교체 — 기존 PROFILE 행 제거 + 옛 객체 best-effort 삭제 후 새 대표 행 저장(가게당 1장 보장).
     *
     * <p>[동시성] read(기존 PROFILE)-delete-insert는 무잠금이면 동시 PROFILE 업로드 2건이 같은 빈/집합을 읽어
     * 각각 insert → PROFILE 2장 잔존(대표 비결정)이 된다. MySQL 부분 unique index 미지원 + GALLERY 다중이라
     * (shop_id,type) 단순 UNIQUE도 못 건다. 따라서 Shop 행을 SELECT ... FOR UPDATE로 잠가 본 구간을 직렬화한다.
     * 락은 (느린) S3 업로드 이후 여기서만 획득 → 보유 시간을 DB 작업 구간으로 최소화한다.
     */
    private ShopImage replaceProfile(Long shopId, Long userId, String objectKey) {
        // 본인 가게 행 비관락 — 동시 PROFILE 업로드를 직렬화(read-delete-insert 원자성 보장). 소유권은 상위에서 검증됨.
        shopRepository.findByIdAndUserIdWithLock(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        // 잠금 읽기로 기존 PROFILE 조회 — 위 비관락 직후라도 일반 SELECT는 early-pinned 스냅샷 탓에 동시 트랜잭션이
        //   커밋한 PROFILE을 놓칠 수 있어, FOR UPDATE로 최신 커밋본을 읽어 확실히 교체한다(stale snapshot 회피).
        for (ShopImage old : shopImageRepository.findByShopIdAndTypeForUpdate(shopId, ShopImageType.PROFILE)) {
            shopImageRepository.delete(old);
            imageStorage.delete(old.getObjectKey());
        }
        return shopImageRepository.save(ShopImage.profile(shopId, objectKey));
    }

    /**
     * GALLERY 추가 — 기존 갤러리 최대 sort_order + 1을 부여해 업로드 순서대로 정렬되게 한다.
     *
     * <p>[동시성] max+1 계산은 비원자라 동시 GALLERY 업로드 2건이 같은 max를 읽어 sort_order가 중복될 수 있다.
     * 단 목록 정렬 2차 키가 id ASC라 결과 순서는 결정적이라 무해 — PROFILE과 달리 직렬화 락을 걸지 않는다.
     * 엄밀한 순서 정합(드래그 재정렬 등)이 필요해지면 후속(미구현API.md B24 계열)에서 재검토.
     */
    private ShopImage appendGallery(Long shopId, String objectKey) {
        int nextOrder = shopImageRepository.findByShopIdAndType(shopId, ShopImageType.GALLERY).stream()
                .mapToInt(ShopImage::getSortOrder)
                .max()
                .orElse(-1) + 1;
        return shopImageRepository.save(ShopImage.gallery(shopId, objectKey, nextOrder));
    }

    /** shopId + userId 조합으로 ACTIVE 가게 조회. 없거나 소유자 불일치면 SHOP_001, 비활성이면 SHOP_005. */
    private Shop getActiveShop(Long userId, Long shopId) {
        Shop shop = shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }
        return shop;
    }

    private void validateFile(MultipartFile file) {
        // Spring @RequestParam 누락 시 서비스 도달 전 400 반환 — null은 실제 발생하지 않음
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_FILE_TOO_LARGE);
        }
        // 1차: declared Content-Type 화이트리스트. null 방어: per-part 헤더 없으면 getContentType()=null.
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
        }
        // 2차: magic-byte 시그니처 — declared가 image/jpeg여도 실제 바이트가 이미지가 아니면 거부(.exe 위장 차단, KAN-188).
        if (!isAllowedImageSignature(file)) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
        }
    }

    /**
     * 매직바이트가 declared content-type과 <b>같은 포맷</b>인지 검사(hyeonmin02 리뷰).
     * "허용 포맷 중 하나"가 아니라 "선언값과 일치"를 봐야 declared=png/실제=jpeg 같은 불일치(확장자·S3 Content-Type 메타 오염)를 막는다.
     */
    private static boolean isAllowedImageSignature(MultipartFile file) {
        byte[] h;
        try {
            h = file.getInputStream().readNBytes(12);
        } catch (IOException e) {
            return false;
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> isJpeg(h);
            case "image/png" -> isPng(h);
            case "image/webp" -> isWebp(h);
            default -> false;
        };
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
}
