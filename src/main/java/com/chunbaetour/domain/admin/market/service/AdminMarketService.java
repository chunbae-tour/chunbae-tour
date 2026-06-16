package com.chunbaetour.domain.admin.market.service;

import com.chunbaetour.domain.admin.market.dto.response.AdminMarketDetailResponse;
import com.chunbaetour.domain.admin.market.dto.response.AdminMarketListResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 운영자 전통시장 조회 서비스 (KAN-308).
 *
 * <p>공공데이터 sync가 원천이라 조회 전용. 목록(keyword/sido 필터 + cursor 페이징)·단건 상세를 제공한다.
 * 수정/숨김/삭제는 원천관리 정책(B13: 시장 soft-delete) 확정 후 별도 슬라이스 — 본 서비스는 읽기만.
 *
 * <p>cursor 페이징·필터는 {@code AdminShopService.getShops}/{@code AdminPlaceService.getPlaces} 패턴을 미러한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMarketService {

    private final TraditionalMarketRepository traditionalMarketRepository;

    /**
     * 전통시장 목록 검색 (KAN-308) — keyword(시장명)·sido 옵션 필터 + cursor 페이징.
     *
     * <p>keyword 공백은 null로 정규화(전체 조회), LIKE 와일드카드는 이스케이프(ESCAPE '\'). 공통 팩토리
     * {@link CursorPageResponse#of}로 hasNext 판별·nextCursor 인코딩·size 요청값 echo·size<1 fail-fast를 일관 처리.
     */
    public CursorPageResponse<AdminMarketListResponse> getMarkets(
            String keyword, String sido, String cursor, int size) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? "%" + escapeLike(keyword.trim()) + "%" : null;
        String normalizedSido = StringUtils.hasText(sido) ? sido.trim() : null;
        Long cursorId = CursorUtils.decodeSafe(cursor);

        List<TraditionalMarket> markets = traditionalMarketRepository.searchForAdmin(
                normalizedKeyword, normalizedSido, cursorId, PageRequest.of(0, size + 1));

        return CursorPageResponse.of(markets, size, AdminMarketListResponse::from, TraditionalMarket::getId);
    }

    /** 전통시장 단건 상세 — 없으면 MARKET_NOT_FOUND(404). */
    public AdminMarketDetailResponse getMarket(Long marketId) {
        TraditionalMarket market = traditionalMarketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        return AdminMarketDetailResponse.from(market);
    }

    /**
     * LIKE 와일드카드 이스케이프 — {@code \ % _}를 리터럴로 처리(ESCAPE '\' 전제). 백슬래시를 먼저 치환해 이중 이스케이프 방지.
     * (AdminShopService/AdminPlaceService.escapeLike와 동일 정책.)
     */
    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
