-- 가게 사진 용도 분리 모델 (KAN-315, ADR-003).
-- Shop.imageUrls 단일 JSON blob을 대체 — 사진마다 용도(PROFILE/GALLERY)·정렬순서·대표플래그를 행 단위로 표현.
-- 백필 불필요(경우 B 확정: 운영 imageUrls=null, 살릴 데이터 없음). Shop.imageUrls 컬럼은 후속 PR에서 드롭.
CREATE TABLE IF NOT EXISTS shop_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id    BIGINT       NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    sort_order INT          NOT NULL,
    is_primary BIT(1)       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_shop_images_shop_id_id (shop_id, id),
    CONSTRAINT fk_shop_images_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
