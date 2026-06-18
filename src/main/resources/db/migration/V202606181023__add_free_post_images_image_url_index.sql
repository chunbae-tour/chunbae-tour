-- 자유게시판 이미지 고아 reconcile(existsByImageKey) 풀스캔 방지 (KAN-317).
-- free_post_images.image_url을 키로 직접 조회하므로 인덱스 추가. (UNIQUE 아님 — 같은 키가 여러 글에 중복될 일은 없으나
--  image_url은 nullable이고 정렬용 PK가 (post_id, sort_order)라 단순 보조 인덱스로 둔다.)
ALTER TABLE free_post_images
    ADD INDEX idx_free_post_images_image_url (image_url);
