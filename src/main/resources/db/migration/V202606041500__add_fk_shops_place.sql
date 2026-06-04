-- shops.place_id → places(id) FK 추가 (KAN-217)
-- ON DELETE SET NULL: Place 삭제 시 shops.place_id 자동 NULL 처리 (고아 참조 방지)
-- place_id는 nullable이므로 NULL 값에 대해서는 FK 검사 없이 허용됨
ALTER TABLE shops
    ADD CONSTRAINT fk_shops_place
    FOREIGN KEY (place_id) REFERENCES places (id)
    ON DELETE SET NULL;
