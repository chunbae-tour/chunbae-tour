package com.chunbaetour.domain.admin.banner.service;

import com.chunbaetour.domain.admin.banner.dto.request.AdminBannerCreateRequest;
import com.chunbaetour.domain.admin.banner.dto.request.AdminBannerUpdateRequest;
import com.chunbaetour.domain.admin.banner.dto.response.AdminBannerDetailResponse;
import com.chunbaetour.domain.admin.banner.dto.response.AdminBannerListResponse;
import com.chunbaetour.domain.banner.Banner;
import com.chunbaetour.domain.banner.repository.BannerRepository;
import com.chunbaetour.domain.banner.type.BannerStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 배너 관리 서비스 (Admin Epic KAN-177 S09, KAN-216).
 *
 * <p>조회는 클래스 기본 {@code @Transactional(readOnly = true)}, 등록/수정/삭제만 쓰기 {@code @Transactional}로
 * override — S07 {@code AdminPlaceService} 패턴 일관. CUD endpoint의 audit 기록은 컨트롤러의
 * {@link com.chunbaetour.domain.admin.audit.LogAdminAction} AOP가 담당하며 본 서비스는 도메인 상태 전이만 한다.
 *
 * <p>삭제는 soft delete만 지원한다 — {@link Banner#delete()}로 {@link BannerStatus#DELETED} 전이. 운영자 목록은
 * DELETED를 제외한다. hard delete는 본 슬라이스 범위 밖.
 *
 * <p>목록 정렬이 (priority ASC, id DESC) 복합이라 cursor도 (priority, id) 복합 keyset이다 — 단일 id cursor인
 * {@code CursorUtils} 대신 {@link #encodeCursor}/{@link #decodeCursor}로 두 값을 함께 인코딩한다.
 *
 * <p>{@link #getTotalBanners()}는 S10 대시보드 의존 — 배너 카운트 쿼리는 본 서비스에만 두고 대시보드는 조합만
 * 한다. 본 슬라이스에서는 메서드 노출까지만(wiring은 S10).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBannerService {

    private static final String CURSOR_DELIMITER = ":";

    private final BannerRepository bannerRepository;

    /**
     * 배너 목록 검색 — status 필터 + (priority ASC, id DESC) 복합 keyset 페이징.
     *
     * <p>size+1 sentinel로 다음 페이지 존재를 추가 쿼리 없이 판단한다. DELETED(soft delete)는 항상 제외.
     */
    public CursorPageResponse<AdminBannerListResponse> getBanners(
            BannerStatus status, String cursor, int size) {
        if (size < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // DELETED는 운영자 목록에서 항상 제외(soft delete) — status=DELETED 필터는 "빈 목록"이 아니라
        // 미지원 입력으로 보고 400으로 거부한다(조용한 빈 결과로 오해 유발 방지).
        if (status == BannerStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Cursor decoded = decodeCursor(cursor);

        PageRequest pageable = PageRequest.of(0, size + 1);
        List<Banner> banners = bannerRepository.searchForAdmin(
                status, decoded == null ? null : decoded.priority(),
                decoded == null ? null : decoded.id(), pageable);

        boolean hasNext = banners.size() > size;
        List<Banner> content = hasNext ? banners.subList(0, size) : banners;
        // 복합 커서(priority+id)라 계산형 of(id추출자)에 안 맞음 → nextCursor는 직접 인코딩하고 조립형 팩토리로 단일화 (KAN-325)
        String nextCursor = hasNext
                ? encodeCursor(content.get(content.size() - 1))
                : null;

        List<AdminBannerListResponse> responses = content.stream()
                .map(AdminBannerListResponse::from)
                .toList();

        // size echo 통일 — 실제 개수(responses.size()) 아닌 요청 size (KAN-325)
        return CursorPageResponse.ofAssembled(responses, nextCursor, hasNext, size);
    }

    /** 배너 등록 — Banner 신규 생성(ACTIVE). 등록된 상세를 반환한다. */
    @Transactional
    public AdminBannerDetailResponse createBanner(AdminBannerCreateRequest request) {
        Banner banner = bannerRepository.save(request.toEntity());
        return AdminBannerDetailResponse.from(banner);
    }

    /**
     * 배너 partial update — {@link Banner#update(...)} null-skip 반영. 없으면 BANNER_NOT_FOUND(404).
     *
     * <p>이미 soft delete(DELETED)된 배너 수정은 {@link ErrorCode#BANNER_ALREADY_DELETED}(409)로 거부한다
     * (deleteBanner 멱등 가드와 동일 정책). 삭제된 리소스를 수정으로 되살리는 듯한 혼란을 막는다.
     */
    @Transactional
    public AdminBannerDetailResponse updateBanner(Long bannerId, AdminBannerUpdateRequest request) {
        Banner banner = bannerRepository.findById(bannerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND));
        if (banner.getStatus() == BannerStatus.DELETED) {
            throw new BusinessException(ErrorCode.BANNER_ALREADY_DELETED);
        }
        banner.update(
                request.title(),
                request.imageUrl(),
                request.linkUrl(),
                request.priority(),
                request.startDate(),
                request.endDate());
        return AdminBannerDetailResponse.from(banner);
    }

    /**
     * 배너 soft delete — {@link Banner#delete()}(→DELETED). 없으면 BANNER_NOT_FOUND(404).
     *
     * <p>이미 DELETED인 배너 재삭제는 {@link ErrorCode#BANNER_ALREADY_DELETED}(409)로 거부한다 — 조용한 멱등
     * 204 대신 운영자에게 "이미 삭제됨"을 명확히 알려 중복 액션/오해를 막는다(S07 deletePlace 정책 미러).
     */
    @Transactional
    public void deleteBanner(Long bannerId) {
        Banner banner = bannerRepository.findById(bannerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND));
        if (banner.getStatus() == BannerStatus.DELETED) {
            throw new BusinessException(ErrorCode.BANNER_ALREADY_DELETED);
        }
        banner.delete();
    }

    // ── S10 대시보드 의존 카운트 (본 슬라이스는 메서드 노출까지) ────────────────────

    /** 전체 배너 수 — soft delete(DELETED) 제외. ACTIVE/HIDDEN만 집계. */
    public long getTotalBanners() {
        return bannerRepository.countByStatusNot(BannerStatus.DELETED);
    }

    /**
     * 노출 중(ACTIVE) 배너 수 — S10 대시보드 의존. 사용자에게 실제 노출되는 배너만 집계
     * (HIDDEN/DELETED 제외) — S09 배너 노출 기준과 일치.
     */
    public long getActiveBannersCount() {
        return bannerRepository.countByStatus(BannerStatus.ACTIVE);
    }

    // ── 복합 cursor (priority, id) 인코딩 ─────────────────────────────────────

    /** keyset cursor 디코딩 결과 — priority와 id를 함께 담는다. */
    private record Cursor(int priority, long id) {}

    /** 마지막 항목의 (priority, id)를 "priority:id" Base64URL cursor로 인코딩. */
    private static String encodeCursor(Banner banner) {
        String raw = banner.getPriority() + CURSOR_DELIMITER + banner.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * "priority:id" Base64URL cursor를 디코딩. null이면 null(첫 페이지), 형식 오류면
     * {@link BusinessException}(INVALID_CURSOR).
     */
    private static Cursor decodeCursor(String cursor) {
        if (cursor == null) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(CURSOR_DELIMITER);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor: " + cursor);
            }
            long id = Long.parseLong(parts[1]);
            if (id < 1L) {
                throw new IllegalArgumentException("Invalid cursor: " + cursor);
            }
            return new Cursor(Integer.parseInt(parts[0]), id);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
