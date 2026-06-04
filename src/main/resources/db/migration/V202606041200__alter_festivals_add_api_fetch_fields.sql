-- KAN-97: festivals 테이블에 외부 API 수집 관련 컬럼 추가
-- source: 등록 출처 (MANUAL / API_FETCH)
-- category: 축제 카테고리 (FESTIVAL — 추후 MARKET, PERFORMANCE 확장)
-- external_id: 공공API contentid — 중복 수집 방지용 dedup 키

ALTER TABLE festivals
    ADD COLUMN source      VARCHAR(20) NOT NULL DEFAULT 'MANUAL'   AFTER status,
    ADD COLUMN category    VARCHAR(20) NOT NULL DEFAULT 'FESTIVAL'  AFTER source,
    ADD COLUMN external_id VARCHAR(50) NULL                         AFTER category;

-- MySQL에서 NULL은 UNIQUE 제약 비교 시 서로 다른 값으로 간주(NULL != NULL)되므로
-- external_id가 NULL인 MANUAL 레코드가 여러 개 존재해도 제약 위반이 발생하지 않는다. 의도된 설계.
ALTER TABLE festivals
    ADD UNIQUE INDEX uq_festivals_external_id (external_id);
