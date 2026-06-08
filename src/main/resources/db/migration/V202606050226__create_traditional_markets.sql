-- 전통시장 엔티티 테이블 생성 (KAN-220)
CREATE TABLE traditional_markets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    lat DECIMAL(10, 7) NOT NULL,
    lng DECIMAL(10, 7) NOT NULL,
    market_type VARCHAR(50),
    phone_number VARCHAR(20),
    homepage_url VARCHAR(500),
    establish_year INT,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL
);

-- 시장명으로 조회하는 쿼리 최적화
CREATE INDEX idx_traditional_markets_name ON traditional_markets(name);

-- 위치 기반 검색 최적화
CREATE INDEX idx_traditional_markets_lat_lng ON traditional_markets(lat, lng);
