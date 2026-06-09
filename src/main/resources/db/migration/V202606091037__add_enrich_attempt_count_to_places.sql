-- 관광지 상세(Tier-2) 수집 재시도 횟수 (KAN-221).
-- 상세 API가 성공했으나 overview가 비어 있는 경우를 누적해, 한도(MAX_ENRICH_ATTEMPTS) 도달 시
-- "설명 없는 관광지"로 확정하고 재수집을 중단한다(빈 overview 무한 재시도로 인한 외부 API 한도 소진 방지).
ALTER TABLE places
    ADD COLUMN enrich_attempt_count INT NOT NULL DEFAULT 0;
