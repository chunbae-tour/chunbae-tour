-- 소셜 로그인 길 A: 이메일 없이 가입 허용 — 카카오 이메일 미동의(비즈니스 검수 미적용) 계정은 이메일이 없을 수 있다.
-- 주 식별자는 oauth_id이며, 이메일은 보조/nullable로 완화한다.
--
-- 완화(NOT NULL → NULL)만 수행한다 — blue-green 배포 중 구·신 버전이 모두 호환된다:
--   - 구버전: 여전히 이메일을 채워 INSERT(NOT NULL 가정 위반 없음)
--   - 신버전: 이메일 null INSERT 허용
-- UNIQUE(email) 제약은 그대로 둔다 — MySQL UNIQUE 인덱스는 NULL을 여러 개 허용하므로
-- 이메일 없는 소셜 계정이 여러 개여도 충돌하지 않는다(중복 식별은 oauth_id/phone_hash 기준).
ALTER TABLE users
    MODIFY COLUMN email VARCHAR(255) NULL;
