-- ============================
-- companions (동행) — 채팅방당 1번만 시작 가능 (chat_room_id UNIQUE), 방장 자동 포함
-- ============================
CREATE TABLE `companions` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `created_at`    datetime(6)  NOT NULL,
  `updated_at`    datetime(6)  NOT NULL,
  `chat_room_id`  bigint       NOT NULL,
  `status`        enum('ONGOING','ENDED') NOT NULL DEFAULT 'ONGOING',
  `started_at`    datetime(6)  NOT NULL,
  `ended_at`      datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_companions_chat_room_id` (`chat_room_id`),
  CONSTRAINT `fk_companions_chat_room` FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================
-- companion_participants (동행 참여자) — 동행당 유저 중복 참여 불가 (companion_id, user_id) UNIQUE
-- ============================
CREATE TABLE `companion_participants` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `created_at`    datetime(6)  NOT NULL,
  `updated_at`    datetime(6)  NOT NULL,
  `companion_id`  bigint       NOT NULL,
  `user_id`       bigint       NOT NULL,
  `added_at`      datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_companion_participant` (`companion_id`, `user_id`),
  CONSTRAINT `fk_companion_participants_companion` FOREIGN KEY (`companion_id`) REFERENCES `companions` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_companion_participants_user`      FOREIGN KEY (`user_id`)      REFERENCES `users` (`id`)      ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
