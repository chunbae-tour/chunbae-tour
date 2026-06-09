-- User item QR use audit fields (KAN-251)
ALTER TABLE user_items
    ADD COLUMN used_at DATETIME(6) NULL,
    ADD COLUMN used_shop_id BIGINT NULL;

CREATE INDEX idx_user_items_used_shop_id ON user_items(used_shop_id);

ALTER TABLE user_items
    ADD CONSTRAINT fk_user_items_used_shop
        FOREIGN KEY (used_shop_id) REFERENCES shops(id);
