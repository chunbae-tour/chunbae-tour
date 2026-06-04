-- 부동 소수점 정합성 및 공간 쿼리 성능 개선을 위한 스키마 리팩토링

-- 1. 기존 인덱스 삭제 (lat, lng, rating을 사용하는 인덱스)
ALTER TABLE places DROP INDEX idx_places_lat_lng;
ALTER TABLE places DROP INDEX idx_places_active_rating_id;
ALTER TABLE places DROP INDEX idx_places_active_category_rating_id;

-- 2. 신규 컬럼 추가
ALTER TABLE places ADD COLUMN location POINT SRID 4326 AFTER address;
ALTER TABLE places ADD COLUMN new_rating INT NOT NULL DEFAULT 0 AFTER admission_fee;

-- 3. 데이터 마이그레이션 (lat/lng -> POINT 변환, float rating -> int 변환)
UPDATE places 
SET location = ST_GeomFromText(CONCAT('POINT(', lng, ' ', lat, ')'), 4326),
    new_rating = CAST(rating * 10 AS UNSIGNED);

-- 4. 제약 조건 강화
ALTER TABLE places MODIFY COLUMN location POINT NOT NULL SRID 4326;

-- 5. 기존 컬럼 삭제 및 새 컬럼 이름 변경
ALTER TABLE places DROP COLUMN lat, DROP COLUMN lng, DROP COLUMN rating;
ALTER TABLE places RENAME COLUMN new_rating TO rating;

-- 6. 인덱스 재생성 (SPATIAL INDEX 추가)
CREATE SPATIAL INDEX idx_places_location ON places(location);
CREATE INDEX idx_places_active_rating_id ON places(status, rating DESC, id DESC);
CREATE INDEX idx_places_active_category_rating_id ON places(status, category, rating DESC, id DESC);
