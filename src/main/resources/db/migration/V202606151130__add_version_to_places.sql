-- KAN-304(B12): 관광지 낙관적 락(@Version) 컬럼 추가.
-- Tier-1 sync(updateFromApi)와 Tier-2 enrich(applyApiDetail) 동시 UPDATE의 lost-update 방지.
-- 기존 행은 DEFAULT 0으로 초기화 — Hibernate @Version이 이후 UPDATE마다 1씩 증가시킨다.
ALTER TABLE places ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
