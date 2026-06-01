-- KAN-95: festivals 테이블 스키마 변경
-- 제거: lat, lng, location, thumbnail_url, image_urls (좌표·구버전 이미지 필드)
-- 추가: address, image_url, related_url
ALTER TABLE festivals
    DROP COLUMN lat,
    DROP COLUMN lng,
    DROP COLUMN location,
    DROP COLUMN thumbnail_url,
    DROP COLUMN image_urls;

ALTER TABLE festivals
    ADD COLUMN address     VARCHAR(255) NOT NULL DEFAULT '' AFTER region,
    ADD COLUMN image_url   VARCHAR(512)          DEFAULT NULL AFTER end_date,
    ADD COLUMN related_url VARCHAR(512)          DEFAULT NULL AFTER image_url;
