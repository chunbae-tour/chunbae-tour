-- ============================
-- support_rooms (고객센터 상담방)
-- ============================
CREATE TABLE `support_rooms` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `user_id`    bigint       NOT NULL,
  `admin_id`   bigint       DEFAULT NULL,
  `status`     varchar(10)  NOT NULL DEFAULT 'WAITING',
  `created_at` datetime(6)  NOT NULL,
  `updated_at` datetime(6)  NOT NULL,
  `closed_at`  datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_support_rooms_user`  FOREIGN KEY (`user_id`)  REFERENCES `users` (`id`),
  CONSTRAINT `fk_support_rooms_admin` FOREIGN KEY (`admin_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================
-- support_messages (상담 메시지)
-- ============================
CREATE TABLE `support_messages` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `support_room_id` bigint       NOT NULL,
  `sender_id`       bigint       NOT NULL,
  `sender_role`     varchar(10)  NOT NULL,
  `message_type`    varchar(10)  NOT NULL,
  -- TEXT 타입: content 필수. IMAGE/FILE 타입: content 선택(캡션), fileUrl 필수
  `content`         text         DEFAULT NULL,
  `file_url`        varchar(500) DEFAULT NULL,
  `sent_at`         datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_support_messages_room`   FOREIGN KEY (`support_room_id`) REFERENCES `support_rooms` (`id`),
  CONSTRAINT `fk_support_messages_sender` FOREIGN KEY (`sender_id`)       REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
