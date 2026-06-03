-- ShedLock 분산 스케줄러 락 테이블 (KAN-205)
-- 다중 인스턴스 환경에서 @Scheduled 중복 실행 방지 — 동일 작업을 한 인스턴스만 실행하도록 보장
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
