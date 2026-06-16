package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * KAN-306 기존 DELETED 데이터 백필(V202606160910) 분류 로직 검증.
 *
 * <p>통합 테스트는 Hibernate create-drop 스키마 + Flyway 비활성이라 마이그레이션 자체는 실행되지 않는다
 * (마이그 적용/순서 검증은 FlywayBaselineIntegrationTest 담당). 본 테스트는 백필 UPDATE의 <b>분류 로직</b>이
 * 실 MySQL에서 의도대로 동작하는지(감사로그 기반 운영자/원천 삭제 분리) 동일 SQL을 실행해 검증한다.
 */
@SpringBootTest
class PlaceSourceDeletedBackfillIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 백필 마이그레이션(V202606160910)과 동일한 UPDATE — 원천 삭제였던 DELETED만 SOURCE_DELETED로 보정
    private static final String BACKFILL_SQL = """
            UPDATE places p
            SET p.status = 'SOURCE_DELETED'
            WHERE p.status = 'DELETED'
              AND p.source = 'API_FETCH'
              AND NOT EXISTS (
                  SELECT 1 FROM admin_action_logs l
                  WHERE l.action_type = 'PLACE_DELETE'
                    AND l.target_id = p.id
              )
            """;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM admin_action_logs WHERE action_type = 'PLACE_DELETE'");
        placeRepository.deleteAll();
    }

    private Place deletedApiPlace(String externalId, String name) {
        Place p = Place.createFromApi(externalId, name, "충청남도 천안시",
                BigDecimal.valueOf(36.8), BigDecimal.valueOf(127.1), null, null, "충청남도", "천안시");
        p.delete(); // DELETED
        return placeRepository.saveAndFlush(p);
    }

    private void insertPlaceDeleteLog(Long placeId) {
        jdbcTemplate.update(
                "INSERT INTO admin_action_logs (admin_user_id, action_type, target_type, target_id, created_at) "
                        + "VALUES (?, 'PLACE_DELETE', 'PLACE', ?, ?)",
                1L, placeId, Timestamp.from(Instant.now()));
    }

    private String statusOf(Long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM places WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("백필: PLACE_DELETE 로그 없는 API_FETCH DELETED만 SOURCE_DELETED로 보정 (KAN-306)")
    void backfillReclassifiesOnlySourceDeleted() {
        // A: 원천 삭제(로그 없음) → 보정 대상
        Long sourceDeleted = deletedApiPlace("bf-1", "원천삭제").getId();
        // B: 운영자 삭제(PLACE_DELETE 로그 있음) → DELETED 유지
        Long operatorDeleted = deletedApiPlace("bf-2", "운영자삭제").getId();
        insertPlaceDeleteLog(operatorDeleted);
        // C: ACTIVE → 손대지 않음
        Place active = Place.createFromApi("bf-3", "정상", "충청남도 천안시",
                BigDecimal.valueOf(36.8), BigDecimal.valueOf(127.1), null, null, "충청남도", "천안시");
        Long activeId = placeRepository.saveAndFlush(active).getId();

        int affected = jdbcTemplate.update(BACKFILL_SQL);

        assertThat(affected).isEqualTo(1);                       // A만 보정
        assertThat(statusOf(sourceDeleted)).isEqualTo("SOURCE_DELETED");
        assertThat(statusOf(operatorDeleted)).isEqualTo("DELETED"); // 운영자 삭제 보존
        assertThat(statusOf(activeId)).isEqualTo("ACTIVE");
    }
}
