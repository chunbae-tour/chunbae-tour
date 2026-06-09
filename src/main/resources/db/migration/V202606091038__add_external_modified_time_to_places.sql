-- 관광지 외부(KorService2) modifiedtime 저장 (KAN-221).
-- Tier-1 동기화에서 외부 modifiedtime이 증가(데이터 갱신)했을 때만 enrich_attempt_count를 리셋하기 위한 기준값.
-- 뒤늦게 overview가 채워진 관광지가 한도 소진으로 영구 제외되는 것을 방지한다.
ALTER TABLE places
    ADD COLUMN external_modified_time VARCHAR(14) NULL;
