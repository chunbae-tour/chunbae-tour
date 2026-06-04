-- KAN-95: festivals 테이블 스키마 변경
-- ADD → backfill → DROP 순서로 기존 데이터 보존
-- 제거: location, thumbnail_url, image_urls
-- 추가: address, image_url, related_url

-- Step 1: 새 컬럼 추가 (nullable)
ALTER TABLE festivals
    ADD COLUMN address     VARCHAR(255) NULL AFTER region,
    ADD COLUMN image_url   VARCHAR(512) NULL AFTER end_date,
    ADD COLUMN related_url VARCHAR(512) NULL AFTER image_url;

-- Step 2: 기존 데이터 이전
UPDATE festivals
SET address   = COALESCE(NULLIF(location, ''), ''),
    image_url = thumbnail_url;

-- Step 3: NOT NULL 제약 적용
ALTER TABLE festivals
    MODIFY COLUMN address VARCHAR(255) NOT NULL DEFAULT '';

-- Step 4: 구버전 컬럼 제거
ALTER TABLE festivals
    DROP COLUMN location,
    DROP COLUMN thumbnail_url,
    DROP COLUMN image_urls;
