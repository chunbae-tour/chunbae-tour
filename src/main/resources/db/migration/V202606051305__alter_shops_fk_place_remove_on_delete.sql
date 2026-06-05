-- shops.place_id FK에서 ON DELETE SET NULL 제거 (KAN-361)
-- Place는 soft delete(status=DELETED)만 사용하므로 DB 행이 실제 삭제되지 않아 ON DELETE SET NULL이 발화하지 않음
-- 연관 shop.place_id null 처리는 AdminPlaceService.deletePlace()가 직접 담당
ALTER TABLE shops DROP FOREIGN KEY fk_shops_place;
ALTER TABLE shops
    ADD CONSTRAINT fk_shops_place
    FOREIGN KEY (place_id) REFERENCES places (id);
