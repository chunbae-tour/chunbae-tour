CREATE TABLE shop_notices (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id    BIGINT       NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_shop_notices_shop_id_id (shop_id, id)
);
