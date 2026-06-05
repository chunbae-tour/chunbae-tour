package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.market.dto.response.MarketApiItem;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전통시장 동기화 배치 저장 서비스.
 * upsertItem은 REQUIRES_NEW 트랜잭션으로 아이템 단위 격리 — 실패해도 전체 동기화 계속.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSyncBatchService {

    private final TraditionalMarketRepository marketRepository;

    enum UpsertResult { INSERTED, UPDATED, SKIPPED }

    /**
     * 단일 시장 데이터 upsert.
     * name + address 복합 키로 기존 데이터 존재 여부 확인 후 INSERT/UPDATE.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UpsertResult upsertItem(MarketApiItem item) {
        try {
            if (item.getMrktNm() == null || item.getMrktNm().isBlank() ||
                item.getRdnmadr() == null || item.getRdnmadr().isBlank() ||
                item.getLatitude() == null || item.getLatitude().isBlank() ||
                item.getLongitude() == null || item.getLongitude().isBlank()) {
                log.warn("[MarketSync] 필수 필드 누락 — skip: name={}", item.getMrktNm());
                return UpsertResult.SKIPPED;
            }

            Optional<TraditionalMarket> existing = marketRepository.findByNameAndAddress(
                item.getMrktNm(), item.getRdnmadr()
            );

            if (existing.isPresent()) {
                TraditionalMarket market = existing.get();
                // toEntity()와 동일한 blank 가드 — malformed 좌표 문자열 방어
                BigDecimal latValue = item.getLatitude() != null && !item.getLatitude().isBlank()
                    ? new BigDecimal(item.getLatitude()) : null;
                BigDecimal lngValue = item.getLongitude() != null && !item.getLongitude().isBlank()
                    ? new BigDecimal(item.getLongitude()) : null;
                market.update(
                    latValue,
                    lngValue,
                    item.getMrktType(),
                    item.getPhoneNumber(),
                    item.getHomepageUrl(),
                    MarketApiItem.parseYearOrNull(item.getEstblYear())
                );
                // saveAndFlush: REQUIRES_NEW 트랜잭션 내 flush 시점 명확화 → 제약 위반을 즉시 catch
                marketRepository.saveAndFlush(market);
                return UpsertResult.UPDATED;
            } else {
                marketRepository.saveAndFlush(item.toEntity());
                return UpsertResult.INSERTED;
            }
        } catch (DataIntegrityViolationException e) {
            // REQUIRES_NEW 트랜잭션은 이미 rollback-only — SKIPPED 반환 불가, re-throw로 호출부 catch에 위임
            log.warn("[MarketSync] DB 제약 위반 — skip: name={}, msg={}", item.getMrktNm(), e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            // 좌표/필드 파싱 실패: 데이터 품질 문제 — warn 레벨로 구분 추적
            log.warn("[MarketSync] 데이터 품질 오류 — skip: name={}, reason={}", item.getMrktNm(), e.getMessage());
            return UpsertResult.SKIPPED;
        } catch (org.springframework.dao.DataAccessException e) {
            // DB/인프라 장애 — 은닉하지 않고 전파 (배치 전체 중단)
            log.error("[MarketSync] DB 접근 오류 — 동기화 중단: name={}", item.getMrktNm(), e);
            throw e;
        } catch (Exception e) {
            log.error("[MarketSync] 예상치 못한 오류 — 동기화 중단: name={}", item.getMrktNm(), e);
            throw new IllegalStateException("전통시장 업서트 중 알 수 없는 오류", e);
        }
    }
}
