CREATE TABLE `companion_reviews` (
  `id`             bigint       NOT NULL AUTO_INCREMENT,
  `created_at`     datetime(6)  NOT NULL,
  `updated_at`     datetime(6)  NOT NULL,
  `reviewer_id`    bigint       NOT NULL,
  `target_user_id` bigint       NOT NULL,
  `chat_room_id`   bigint       NOT NULL,
  `score`          int          NOT NULL,
  `content`        text,
  CONSTRAINT `chk_companion_review_score` CHECK (`score` BETWEEN 1 AND 5),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_companion_review` (`reviewer_id`, `target_user_id`, `chat_room_id`),
  INDEX `idx_companion_reviews_target_user_id` (`target_user_id`),
  INDEX `idx_companion_reviews_chat_room_id` (`chat_room_id`),
  CONSTRAINT `fk_companion_reviews_reviewer`    FOREIGN KEY (`reviewer_id`)    REFERENCES `users` (`id`)      ON DELETE RESTRICT,
  CONSTRAINT `fk_companion_reviews_target_user` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`)      ON DELETE RESTRICT,
  CONSTRAINT `fk_companion_reviews_chat_room`   FOREIGN KEY (`chat_room_id`)   REFERENCES `chat_rooms` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
