-- KAN-221: 관광지 외부 API(한국관광공사 KorService2) 동기화 지원 컬럼 추가.
-- external_id : KorService2 contentid 저장 → 배치 upsert 의 dedup 키. 수동 등록(MANUAL) 관광지는 NULL.
--               MySQL UNIQUE 인덱스는 NULL 중복을 허용하므로 수동 등록 다건이 서로 충돌하지 않는다.
-- source      : 데이터 출처 구분(MANUAL/API_FETCH). 기존 행은 전부 수동 등록이므로 DEFAULT 'MANUAL'.
ALTER TABLE places
    ADD COLUMN external_id VARCHAR(64) NULL,
    ADD COLUMN source      VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

-- contentid 중복 적재 방지. NULL 다건 허용(수동 등록 관광지).
ALTER TABLE places
    ADD CONSTRAINT uk_places_external_id UNIQUE (external_id);
