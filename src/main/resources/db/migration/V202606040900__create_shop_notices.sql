CREATE TABLE IF NOT EXISTS shop_notices (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id    BIGINT       NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_shop_notices_shop_id_id (shop_id, id),
    CONSTRAINT fk_shop_notices_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
