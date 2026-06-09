-- =====================================================================
-- V1 Baseline — chunbae-tour 운영 prod 스키마 (KAN-178)
-- =====================================================================
-- 추출 환경: 운영 책임자 로컬 Docker MySQL 8.4 (chunbae-tour-mysql)
-- 추출 명령: mysqldump --no-data --skip-comments
-- 추출 일: 2026-05-29
-- 적용 정책: baseline-on-migrate=true + baseline-version=1
--            → 기존 운영 DB는 schema 그대로 보존. Flyway가 schema_history에 V1만 기록.
--            → 빈 DB(local/dev/CI testcontainers)는 본 SQL이 그대로 실행됨.
-- CREATE TABLE 내용(컬럼/인덱스/FK)은 raw dump 그대로. 메타 라인만 제거.
-- =====================================================================

-- 알파벳 순서 dump이므로 chat_room_members(chat_rooms FK), free_post_images(free_posts FK),
-- user_likes(users/places FK) 등 forward FK가 발생한다. 단일 batch 적용 시 FK 검증을
-- 일시 비활성화해야 한다 (raw dump의 FOREIGN_KEY_CHECKS=0 정책과 동일).
-- 적용 종료 직전 다시 1로 복원해 후속 마이그레이션에 영향 없음.
SET FOREIGN_KEY_CHECKS = 0;


-- ============================
-- ad_applications (광고 신청)
-- ============================
CREATE TABLE `ad_applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `ad_type` enum('BANNER') NOT NULL,
  `cost` bigint NOT NULL,
  `end_date` date NOT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `shop_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ad_applications_shop_id` (`shop_id`),
  KEY `idx_ad_applications_shop_id_status` (`shop_id`,`status`),
  KEY `idx_ad_applications_status_id` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- chat_room_members (채팅방 멤버)
-- ============================
CREATE TABLE `chat_room_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `joined_at` datetime(6) NOT NULL,
  `left_at` datetime(6) DEFAULT NULL,
  `member_state` enum('MEMBER_ACTIVE','MEMBER_KICKED','MEMBER_LEFT','OWNER_ACTIVE') NOT NULL,
  `user_id` bigint NOT NULL,
  `chat_room_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpal8hkw0sfg71i5v05l0ojhq1` (`chat_room_id`,`user_id`),
  CONSTRAINT `FK6x7kwk21yt5odfxv5ubbrmo0h` FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- chat_rooms (채팅방)
-- ============================
CREATE TABLE `chat_rooms` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `current_members` int NOT NULL,
  `description` text,
  `max_members` int NOT NULL,
  `owner_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `status` enum('CLOSED','FULL','OPEN') NOT NULL,
  `title` varchar(50) NOT NULL,
  `version` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_rooms_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- comments (댓글)
-- ============================
CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `author_id` bigint NOT NULL,
  `content` varchar(1000) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `post_id` bigint NOT NULL,
  `post_type` enum('COMPANION','FREE') NOT NULL,
  `status` enum('ACTIVE','DELETED') NOT NULL,
  `parent_comment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_comment_post` (`post_id`,`post_type`,`parent_comment_id`,`id`),
  KEY `idx_comment_parent` (`parent_comment_id`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- common_error_logs (공통 에러 로그)
-- ============================
CREATE TABLE `common_error_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `detail` text,
  `domain` enum('AI_FAQ','TRANSLATION') NOT NULL,
  `error_type` enum('API_CALL_FAILURE','INTERNAL','RATE_LIMIT_EXCEEDED','TIMEOUT') NOT NULL,
  `external_provider` varchar(50) DEFAULT NULL,
  `message` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_common_error_logs_domain_created` (`domain`,`created_at`)
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
  UNIQUE KEY `uq_companion_participant` (`companion_id`,`user_id`),
  KEY `idx_companion_participants_user_id` (`user_id`),
  CONSTRAINT `fk_companion_participants_companion` FOREIGN KEY (`companion_id`) REFERENCES `companions` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_companion_participants_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- companion_posts (동행 게시글)
-- ============================
CREATE TABLE `companion_posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `author_id` bigint NOT NULL,
  `content` text NOT NULL,
  `current_members` int NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `max_members` int NOT NULL,
  `meeting_date` date NOT NULL,
  `place_id` bigint NOT NULL,
  `place_name` varchar(100) NOT NULL,
  `region` varchar(50) DEFAULT NULL,
  `status` enum('ACTIVE','CLOSED','DELETED','HIDDEN') NOT NULL,
  `title` varchar(200) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- companions (동행) — 채팅방당 1번만 시작 가능 (chat_room_id UNIQUE), 방장 자동 포함
-- ============================
CREATE TABLE `companions` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `created_at`    datetime(6)  NOT NULL,
  `updated_at`    datetime(6)  NOT NULL,
  `chat_room_id`  bigint       NOT NULL,
  `status`        enum('ONGOING','ENDED') NOT NULL,
  `started_at`    datetime(6)  NOT NULL,
  `ended_at`      datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_companions_chat_room_id` (`chat_room_id`),
  CONSTRAINT `fk_companions_chat_room` FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- festivals (축제/행사)
-- ============================
CREATE TABLE `festivals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` text,
  `end_date` date NOT NULL,
  `image_urls` json DEFAULT NULL,
  `lat` decimal(10,7) NOT NULL,
  `lng` decimal(11,7) NOT NULL,
  `location` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `region` varchar(100) NOT NULL,
  `start_date` date NOT NULL,
  `status` enum('ACTIVE','DELETED','HIDDEN') NOT NULL,
  `thumbnail_url` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- free_post_images (자유 게시글 이미지)
-- ============================
CREATE TABLE `free_post_images` (
  `post_id` bigint NOT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`post_id`,`sort_order`),
  CONSTRAINT `FK3dhv3esjxgkmpj45vnno1fb72` FOREIGN KEY (`post_id`) REFERENCES `free_posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- free_posts (자유 게시글)
-- ============================
CREATE TABLE `free_posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `author_id` bigint NOT NULL,
  `content` text NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','DELETED','HIDDEN') NOT NULL,
  `title` varchar(200) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- join_requests (채팅방 참여 신청)
-- ============================
CREATE TABLE `join_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `chat_room_id` bigint NOT NULL,
  `message` text,
  `pending_key` bigint DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_join_requests_room_pending_user` (`chat_room_id`,`pending_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- menus (메뉴)
-- ============================
CREATE TABLE `menus` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `description` text,
  `image_url` varchar(500) DEFAULT NULL,
  `is_available` bit(1) NOT NULL,
  `name` varchar(100) NOT NULL,
  `price` bigint NOT NULL,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- merchant_applications (상인 신청)
-- ============================
CREATE TABLE `merchant_applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active_flag` varchar(1) DEFAULT NULL,
  `address` varchar(255) NOT NULL,
  `business_number` varchar(20) NOT NULL,
  `category` varchar(50) NOT NULL,
  `description` text,
  `lat` decimal(10,7) DEFAULT NULL,
  `lng` decimal(10,7) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `shop_name` varchar(50) NOT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_active_business_number` (`business_number`,`active_flag`),
  UNIQUE KEY `uk_merchant_active_user_id` (`user_id`,`active_flag`),
  KEY `idx_merchant_applications_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- messages (채팅 메시지)
-- ============================
CREATE TABLE `messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `chat_room_id` bigint NOT NULL,
  `content` text,
  `file_name` varchar(255) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `file_url` varchar(500) DEFAULT NULL,
  `message_type` enum('FILE','IMAGE','SYSTEM','TEXT') NOT NULL,
  `sender_id` bigint DEFAULT NULL,
  `translate_lang` varchar(10) DEFAULT NULL,
  `translated_content` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- notifications (알림)
-- ============================
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `is_read` bit(1) NOT NULL,
  `message` text NOT NULL,
  `reference_id` bigint NOT NULL,
  `reference_type` enum('CHAT_ROOM','JOIN_REQUEST','SUPPORT_ROOM') NOT NULL,
  `title` varchar(100) NOT NULL,
  `type` enum('CHAT_JOIN_APPROVED','CHAT_JOIN_REJECTED','CHAT_JOIN_REQUEST','CHAT_MEMBER_KICKED','CHAT_MESSAGE','SUPPORT_MESSAGE','SUPPORT_ROOM_CLOSED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_id_id` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- payment_orders (결제 주문)
-- ============================
CREATE TABLE `payment_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount` bigint NOT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `order_uid` varchar(36) NOT NULL,
  `payment_method` enum('CARD','FOREIGN_CARD','KAKAO_PAY','TOSS_PAY') NOT NULL,
  `pg_order_id` varchar(100) DEFAULT NULL,
  `pg_transaction_id` varchar(100) DEFAULT NULL,
  `status` enum('CANCELLED','COMPLETED','FAILED','PENDING','REFUNDED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8o3s8abyjn2gisvwkfqelve79` (`idempotency_key`),
  UNIQUE KEY `UKsojk2yn5cvns4qragypcsefi7` (`order_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- places (관광지/전통시장)
-- ============================
CREATE TABLE `places` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) NOT NULL,
  `admission_fee` varchar(50) DEFAULT NULL,
  `category` enum('TOURIST_SPOT','TRADITIONAL_MARKET') NOT NULL,
  `closed_days` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `image_urls` json DEFAULT NULL,
  `lat` decimal(10,7) NOT NULL,
  `like_count` int NOT NULL,
  `lng` decimal(10,7) NOT NULL,
  `name` varchar(100) NOT NULL,
  `operating_hours` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `rating` float NOT NULL,
  `review_count` int NOT NULL,
  `status` enum('ACTIVE','DELETED','HIDDEN') NOT NULL,
  `tags` json DEFAULT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `view_count` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_places_category` (`category`),
  KEY `idx_places_status` (`status`),
  KEY `idx_places_lat_lng` (`lat`,`lng`),
  KEY `idx_places_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- products (스토어 상품)
-- ============================
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `category` varchar(50) NOT NULL,
  `description` text,
  `image_urls` json DEFAULT NULL,
  `merchant_name` varchar(100) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `original_price` bigint DEFAULT NULL,
  `original_stock` int NOT NULL,
  `price` bigint NOT NULL,
  `status` enum('HIDDEN','ON_SALE','SOLD_OUT') NOT NULL,
  `stock` int NOT NULL,
  `validity_days` int DEFAULT NULL,
  `max_per_person` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- qr_pay_requests (QR 결제 요청)
-- ============================
CREATE TABLE `qr_pay_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount` bigint NOT NULL,
  `expired_at` datetime(6) NOT NULL,
  `menu_items` json NOT NULL,
  `pay_request_id` varchar(36) NOT NULL,
  `pending_key` varchar(255) DEFAULT NULL,
  `reject_reason` text,
  `shop_id` bigint NOT NULL,
  `status` enum('COMPLETED','EXPIRED','PENDING','REJECTED') NOT NULL,
  `user_id` bigint NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4dd0iekgyykrusickcgh0ctj9` (`pay_request_id`),
  UNIQUE KEY `UKqkyd63xpmd7fdnocvodqwebbt` (`pending_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- refunds (환불)
-- ============================
CREATE TABLE `refunds` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount` bigint NOT NULL,
  `payment_order_id` bigint NOT NULL,
  `reason` varchar(500) NOT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `status` enum('APPROVED','CANCELLED','PENDING','REJECTED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- reports (신고)
-- ============================
CREATE TABLE `reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `reason` enum('HARASSMENT','ILLEGAL','MISINFORMATION','OBSCENE','OTHER','SPAM') NOT NULL,
  `reporter_id` bigint NOT NULL,
  `status` enum('DISMISSED','PENDING','RESOLVED') NOT NULL,
  `target_id` bigint NOT NULL,
  `target_type` enum('COMMENT','MERCHANT','POST_COMPANION','POST_FREE','USER') NOT NULL,
  `action` enum('DELETE','DISMISS','HIDE_SHOP','REVOKE_MERCHANT','SUSPEND','WARNING') DEFAULT NULL,
  `admin_note` text,
  `resolved_at` datetime(6) DEFAULT NULL,
  `resolved_by` varchar(100) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reports_reporter_target` (`reporter_id`,`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- settlements (정산 신청)
-- ============================
CREATE TABLE `settlements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `account_holder` varchar(50) NOT NULL,
  `account_number` varchar(30) NOT NULL,
  `amount` bigint NOT NULL,
  `bank_name` varchar(50) NOT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `shop_id` bigint NOT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_settlements_shop_id` (`shop_id`),
  KEY `idx_settlements_shop_id_status` (`shop_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- shop_wallets (가게 지갑)
-- ============================
CREATE TABLE `shop_wallets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `account_holder` varchar(50) DEFAULT NULL,
  `account_number` varchar(30) DEFAULT NULL,
  `balance` bigint NOT NULL,
  `bank_name` varchar(50) DEFAULT NULL,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_wallets_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- shops (가게)
-- ============================
CREATE TABLE `shops` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `address` varchar(255) NOT NULL,
  `application_id` bigint NOT NULL,
  `category` varchar(50) NOT NULL,
  `closed_days` varchar(100) DEFAULT NULL,
  `description` text,
  `image_urls` json DEFAULT NULL,
  `is_certified` bit(1) NOT NULL,
  `lat` decimal(10,7) DEFAULT NULL,
  `lng` decimal(10,7) DEFAULT NULL,
  `operating_hours` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `rating` double NOT NULL,
  `review_count` int NOT NULL,
  `shop_name` varchar(50) NOT NULL,
  `status` enum('ACTIVE','CLOSED','SUSPENDED') NOT NULL,
  `user_id` bigint NOT NULL,
  `version` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shops_user_id` (`user_id`),
  UNIQUE KEY `uk_shops_application_id` (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- store_orders (스토어 주문)
-- ============================
CREATE TABLE `store_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(100) NOT NULL,
  `product_price` bigint NOT NULL,
  `quantity` int NOT NULL,
  `status` enum('CANCELLED','COMPLETED') NOT NULL,
  `total_price` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_store_order_cursor` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- user_items (사용자 보유 아이템)
-- ============================
CREATE TABLE `user_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `expires_at` date DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(100) NOT NULL,
  `status` enum('AVAILABLE','EXPIRED','USED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_item_user_id_id` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- user_likes (사용자 찜)
-- ============================
CREATE TABLE `user_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `place_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_likes_user_place` (`user_id`,`place_id`),
  KEY `idx_user_likes_user` (`user_id`),
  KEY `idx_user_likes_place` (`place_id`),
  CONSTRAINT `FK6aog39hkl1hs1amxef5i9g4fv` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKi1t2n75olpy8616xgn4k9ynbw` FOREIGN KEY (`place_id`) REFERENCES `places` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- users (사용자 계정)
-- ============================
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `companion_review_count` int NOT NULL,
  `companion_score` float NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `language` varchar(10) NOT NULL,
  `nickname` varchar(20) NOT NULL,
  `password` varchar(255) NOT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  `role` enum('ADMIN','MERCHANT','USER') NOT NULL,
  `status` enum('ACTIVE','DELETED','SUSPENDED') NOT NULL,
  `suspended_until` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK2ty1xmrrgtn89xt7kyxx6ta7h` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- wallets (사용자 지갑/엽전)
-- ============================
CREATE TABLE `wallets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `balance` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallets_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================
-- yeopjeon_histories (엽전 이력)
-- ============================
CREATE TABLE `yeopjeon_histories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount` bigint NOT NULL,
  `balance_snapshot` bigint NOT NULL,
  `description` varchar(100) DEFAULT NULL,
  `payment_order_id` bigint DEFAULT NULL,
  `shop_id` bigint DEFAULT NULL,
  `type` enum('CHARGE','PAYMENT','RECEIVED_PAYMENT','REFUND') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_yeopjeon_history_cursor` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- FK 검증 복원. 후속 마이그레이션 + 일반 트랜잭션에 영향 없도록 1로 복귀.
SET FOREIGN_KEY_CHECKS = 1;
