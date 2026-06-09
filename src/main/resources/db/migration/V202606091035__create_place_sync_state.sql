-- KAN-221: 관광지 증분 동기화 상태 테이블.
-- 마지막으로 동기화한 최신 modifiedtime(yyyyMMddHHmmss)을 저장해, 다음 동기화 때 그 이후 변경분만 수집한다.
-- 전수 재적재(갈아엎기) 대신 변경분만 처리 → DB UPDATE·외부 API 호출 최소화. 단일 행(id=1)만 사용한다.
CREATE TABLE place_sync_state (
    id                 BIGINT       NOT NULL,
    last_modified_time VARCHAR(14)  NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
