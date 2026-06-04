-- [KAN-218] GET /api/v1/places 목록 API 정렬 커버 인덱스 추가
--
-- 신규 공개 목록 API의 기본 쿼리:
--   WHERE status = 'ACTIVE'
--   [AND category = ?]        -- 카테고리 필터 (선택)
--   [AND address LIKE '%?%']  -- 지역 필터 (선택, B-Tree 미사용)
--   ORDER BY rating DESC, id DESC
--
-- 기존 idx_places_status_id (status, id)는 rating 정렬을 커버하지 못해
-- 데이터 증가 시 filesort 발생 가능성이 높음.
--
-- 인덱스 전략:
--   1. idx_places_active_rating_id: 필터 없는 전체 목록 + 카테고리 필터 없을 때 커버
--      → status = ACTIVE + rating DESC + id DESC 정렬 최적화
--
--   2. idx_places_active_category_rating_id: 카테고리 필터 있을 때 커버
--      → status = ACTIVE + category = ? + rating DESC + id DESC 정렬 최적화
--
-- region LIKE '%...%' 는 선행 와일드카드로 B-Tree 인덱스 사용 불가 (Tech Debt: 향후 FULLTEXT 또는 별도 전략 필요)

CREATE INDEX idx_places_active_rating_id
    ON places (status, rating, id);

CREATE INDEX idx_places_active_category_rating_id
    ON places (status, category, rating, id);
