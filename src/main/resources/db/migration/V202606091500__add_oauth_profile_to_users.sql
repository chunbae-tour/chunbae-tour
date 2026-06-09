-- 소셜 로그인(카카오/네이버) 도입 — users 테이블에 OAuth 연결 + 프로필(실명/전화/생년월일) 컬럼 추가.
--
-- 정책:
--   - 소셜 가입자는 비밀번호가 없으므로 password를 NULL 허용으로 완화한다.
--     (기존 로컬 계정은 그대로 NOT NULL 값을 보유 — 데이터 변경 없음, 제약만 완화)
--   - 중복가입 방지 기준 = 전화번호(UNIQUE). 소셜 신원 식별 = (oauth_provider, oauth_id) UNIQUE.
--   - MySQL UNIQUE 인덱스는 NULL을 여러 개 허용하므로 기존 로컬 계정(phone/oauth_* = NULL)은
--     서로 충돌하지 않는다 → 백필 없이 안전.
--   - 생년월일은 '저장만'(미성년자 기능 제한 없음, KAN 결정). 전화번호는 입력만(SMS 인증 없음).
ALTER TABLE users
    ADD COLUMN name           VARCHAR(50)  NULL,
    ADD COLUMN phone          VARCHAR(20)  NULL,
    ADD COLUMN birthdate      DATE         NULL,
    ADD COLUMN oauth_provider VARCHAR(20)  NULL,
    ADD COLUMN oauth_id       VARCHAR(100) NULL,
    MODIFY COLUMN password    VARCHAR(255) NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_phone UNIQUE (phone);

ALTER TABLE users
    ADD CONSTRAINT uk_users_oauth_provider_id UNIQUE (oauth_provider, oauth_id);
