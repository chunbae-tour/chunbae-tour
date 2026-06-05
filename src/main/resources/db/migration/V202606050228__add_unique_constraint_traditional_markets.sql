-- 전통시장 복합 UNIQUE 제약 추가 (upsert 중복 방지)
ALTER TABLE traditional_markets
ADD CONSTRAINT uk_traditional_markets_name_address UNIQUE KEY (name, address);
