-- lat/lng를 임시로 NULL 허용으로 변경.
-- 지도 Geocoding 연동 완료 후 NOT NULL로 복구 예정.
ALTER TABLE festivals
    MODIFY COLUMN lat  decimal(10,7) NULL,
    MODIFY COLUMN lng  decimal(11,7) NULL;
