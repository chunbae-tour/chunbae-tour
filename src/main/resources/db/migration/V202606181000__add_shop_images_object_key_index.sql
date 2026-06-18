-- 가게 사진 고아 reconcile(existsByObjectKey) 풀스캔 방지 (KAN-319 리뷰 반영).
-- object_key는 업로드마다 UUID로 생성돼 자연 유일 → UNIQUE 인덱스로 조회 가속 + 키 중복 방지 겸함.
-- VARCHAR(512) utf8mb4 = 최대 2048바이트 < InnoDB 인덱스 키 한도(3072, DYNAMIC) → 인덱스 가능.
ALTER TABLE shop_images
    ADD CONSTRAINT uq_shop_images_object_key UNIQUE (object_key);
