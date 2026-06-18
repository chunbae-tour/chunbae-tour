-- KAN-322: 동행 게시글 만남 대상을 장소 전용(place_id)에서 다형 참조로 확장.
-- PLACE/MARKET/FESTIVAL을 (target_type, target_id, target_name)으로 표현한다.

-- Step 1: 새 컬럼 추가 (nullable로 추가 후 백필)
ALTER TABLE companion_posts
    ADD COLUMN target_type VARCHAR(20)  NULL AFTER content,
    ADD COLUMN target_id   BIGINT       NULL AFTER target_type,
    ADD COLUMN target_name VARCHAR(100) NULL AFTER target_id;

-- Step 2: 기존 데이터 백필 — 기존 글은 모두 장소(PLACE) 대상
UPDATE companion_posts
SET target_type = 'PLACE',
    target_id   = place_id,
    target_name = place_name
WHERE place_id IS NOT NULL;

-- Step 3: NOT NULL 제약 적용
ALTER TABLE companion_posts
    MODIFY COLUMN target_type VARCHAR(20)  NOT NULL,
    MODIFY COLUMN target_id   BIGINT       NOT NULL,
    MODIFY COLUMN target_name VARCHAR(100) NOT NULL;

-- Step 4: 구버전 컬럼 제거
ALTER TABLE companion_posts
    DROP COLUMN place_id,
    DROP COLUMN place_name;
