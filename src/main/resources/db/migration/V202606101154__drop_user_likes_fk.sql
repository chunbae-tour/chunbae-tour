-- 1155 마이그레이션이 실패하지 않도록 먼저 외래키를 삭제합니다. (Forward-Fix)
ALTER TABLE user_likes DROP FOREIGN KEY FKi1t2n75olpy8616xgn4k9ynbw;
