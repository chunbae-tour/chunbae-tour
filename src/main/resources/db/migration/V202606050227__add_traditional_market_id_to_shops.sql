-- shops 테이블에 traditional_market_id FK 추가 (KAN-220)
-- 가게가 전통시장에 소속될 수 있도록 연결 (nullable, 모든 가게가 시장에 속하는 것은 아님)
ALTER TABLE shops
    ADD COLUMN traditional_market_id BIGINT,
    ADD CONSTRAINT fk_shops_traditional_market
    FOREIGN KEY (traditional_market_id) REFERENCES traditional_markets (id)
    ON DELETE SET NULL;

-- 시장별 가게 조회 성능 최적화
CREATE INDEX idx_shops_traditional_market_id ON shops(traditional_market_id);
