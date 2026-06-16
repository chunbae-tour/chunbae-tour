-- PR1 feat/sanction-schema
-- (1) 제재 이력 테이블 신설
-- (2) users 테이블에 현재 제재 상태 컬럼 추가 (모두 DEFAULT NULL — zero-downtime additive)
-- (3) reports 테이블에 reported_user_id 추가 (도메인별 누적 카운트용)

CREATE TABLE user_sanction (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    report_id     BIGINT       NOT NULL,
    target_type   VARCHAR(20)  NOT NULL,
    sanction_type VARCHAR(20)  NOT NULL,
    reason        VARCHAR(500) NOT NULL,
    started_at    DATETIME     NOT NULL,
    ended_at      DATETIME     NULL,
    released_at   DATETIME     NULL,
    released_by   BIGINT       NULL,
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_sanction_user_id
    ON user_sanction (user_id);

CREATE INDEX idx_user_sanction_user_id_target
    ON user_sanction (user_id, target_type);

CREATE INDEX idx_user_sanction_user_active
    ON user_sanction (user_id, target_type, released_at, ended_at);

ALTER TABLE users
    ADD COLUMN sanction_type    VARCHAR(20) NULL DEFAULT NULL,
    ADD COLUMN sanction_end_at  DATETIME    NULL DEFAULT NULL;

CREATE INDEX idx_users_sanction_end_at
    ON users (sanction_end_at)
    ALGORITHM = INPLACE LOCK = NONE;

ALTER TABLE reports
    ADD COLUMN reported_user_id BIGINT NULL DEFAULT NULL;

CREATE INDEX idx_reports_reported_user_id
    ON reports (reported_user_id)
    ALGORITHM = INPLACE LOCK = NONE;
