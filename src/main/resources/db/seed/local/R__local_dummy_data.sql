-- =====================================================================
-- Local repeatable seed data for frontend/backend integration testing.
-- Loaded only by application-local.yml via classpath:db/seed/local.
-- Login password for all seed accounts: Pa$$w0rd1!
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

SET @seed_password = '$2a$10$Wi1R5TfZd9NTJ2f9QZ01W.MDugzM604D1HDwauUcdrBjW5XTsjliK';

-- ---------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------
INSERT INTO users (
    id, companion_review_count, companion_score, created_at, deleted_at,
    email, language, nickname, password, profile_image_url, role, status,
    suspended_until, updated_at
) VALUES
    (9001, 3, 4.8, NOW(6), NULL, 'user@test.com', 'ko', '춘배여행자', @seed_password,
     'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300&h=300&fit=crop', 'USER', 'ACTIVE', NULL, NOW(6)),
    (9002, 12, 4.9, NOW(6), NULL, 'merchant@test.com', 'ko', '춘배상인', @seed_password,
     'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&h=300&fit=crop', 'MERCHANT', 'ACTIVE', NULL, NOW(6)),
    (9003, 0, 0.0, NOW(6), NULL, 'admin@test.com', 'ko', '춘배관리자', @seed_password,
     NULL, 'ADMIN', 'ACTIVE', NULL, NOW(6)),
    (9004, 1, 4.5, NOW(6), NULL, 'friend@test.com', 'en', '서울친구', @seed_password,
     'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300&h=300&fit=crop', 'USER', 'ACTIVE', NULL, NOW(6))
ON DUPLICATE KEY UPDATE
    password = @seed_password,
    role = VALUES(role),
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = NOW(6);

INSERT INTO wallets (id, created_at, updated_at, balance, user_id) VALUES
    (9001, NOW(6), NOW(6), 150000, 9001),
    (9002, NOW(6), NOW(6), 50000, 9002),
    (9004, NOW(6), NOW(6), 30000, 9004)
ON DUPLICATE KEY UPDATE
    balance = VALUES(balance),
    updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Places and festivals
-- ---------------------------------------------------------------------
INSERT INTO places (
    id, address, admission_fee, category, closed_days, created_at, description,
    image_urls, lat, like_count, lng, name, operating_hours, phone, rating,
    review_count, status, tags, thumbnail_url, updated_at, view_count
) VALUES
    (9101, '서울특별시 종로구 사직로 161', '성인 3,000원', 'TOURIST_SPOT', '매주 화요일', NOW(6),
     '조선 왕조의 중심 궁궐로 한복 산책과 야간 관람 코스가 인기입니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1578922746465-3a80a228f223?w=1200'),
     37.5796170, 128, 126.9770410, '경복궁', '09:00-18:00', '02-3700-3900', 4.8, 532,
     'ACTIVE', JSON_ARRAY('궁궐', '한복', '역사'), 'https://images.unsplash.com/photo-1578922746465-3a80a228f223?w=600', NOW(6), 2048),
    (9102, '서울특별시 종로구 창경궁로 88', NULL, 'TRADITIONAL_MARKET', '일부 점포 상이', NOW(6),
     '먹거리와 한복, 생활 잡화를 한 번에 둘러볼 수 있는 대표 전통시장입니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=1200'),
     37.5700390, 96, 126.9996030, '광장시장', '09:00-22:00', '02-2267-0291', 4.6, 421,
     'ACTIVE', JSON_ARRAY('시장', '먹거리', '빈대떡'), 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600', NOW(6), 1802),
    (9103, '부산광역시 해운대구 달맞이길 62번길', NULL, 'TOURIST_SPOT', NULL, NOW(6),
     '바다와 도시 야경을 함께 볼 수 있는 부산 대표 산책 코스입니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1534274867514-d5b47ef89ed7?w=1200'),
     35.1586980, 73, 129.1603840, '해운대 달맞이길', '상시 개방', NULL, 4.5, 210,
     'ACTIVE', JSON_ARRAY('바다', '산책', '야경'), 'https://images.unsplash.com/photo-1534274867514-d5b47ef89ed7?w=600', NOW(6), 971)
ON DUPLICATE KEY UPDATE
    like_count = VALUES(like_count),
    review_count = VALUES(review_count),
    view_count = VALUES(view_count),
    status = 'ACTIVE',
    updated_at = NOW(6);

INSERT INTO festivals (
    id, created_at, updated_at, description, end_date, image_urls, lat, lng,
    location, name, region, start_date, status, thumbnail_url
) VALUES
    (9201, NOW(6), NOW(6), '한강 야경과 푸드트럭, 버스킹을 함께 즐기는 봄맞이 로컬 축제입니다.',
     '2026-06-15', JSON_ARRAY('https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=1200'),
     37.5269800, 126.9348300, '여의도 한강공원', '한강 달빛 야시장', '서울', '2026-06-01',
     'ACTIVE', 'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=600'),
    (9202, NOW(6), NOW(6), '전통 등과 미디어아트가 어우러지는 야간 산책 행사입니다.',
     '2026-07-07', JSON_ARRAY('https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1200'),
     37.5729500, 126.9769200, '청계천 일대', '청계천 빛 축제', '서울', '2026-06-20',
     'ACTIVE', 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600')
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    updated_at = NOW(6);

INSERT INTO user_likes (id, created_at, place_id, user_id) VALUES
    (9301, NOW(6), 9101, 9001),
    (9302, NOW(6), 9102, 9001),
    (9303, NOW(6), 9103, 9004)
ON DUPLICATE KEY UPDATE
    created_at = VALUES(created_at);

-- ---------------------------------------------------------------------
-- Merchant, shop, menu, QR/pay related data
-- ---------------------------------------------------------------------
INSERT INTO merchant_applications (
    id, created_at, updated_at, active_flag, address, business_number, category,
    description, lat, lng, phone, reject_reason, shop_name, status, user_id
) VALUES
    (9401, NOW(6), NOW(6), 'Y', '서울특별시 종로구 자하문로 15길 12', '123-45-67890', 'CAFE',
     '한옥 골목에서 전통차와 디저트를 판매하는 로컬 카페입니다.',
     37.5808120, 126.9706420, '02-1234-5678', NULL, '춘배다방', 'APPROVED', 9002),
    (9402, NOW(6), NOW(6), 'Y', '서울특별시 종로구 창경궁로 88 광장시장 2구역', '234-56-78901', 'FOOD',
     '3대째 이어온 광장시장 대표 녹두 빈대떡 전문점입니다.',
     37.5700390, 126.9996030, '02-2267-1111', NULL, '순희네 빈대떡', 'APPROVED', 9002),
    (9403, NOW(6), NOW(6), 'Y', '서울특별시 종로구 창경궁로 88 광장시장 1구역', '345-67-89012', 'FOOD',
     '신선한 한우 육회와 바삭한 빈대떡으로 유명한 광장시장 맛집입니다.',
     37.5701200, 126.9994500, '02-2267-2222', NULL, '박가네 빈대떡+육회', 'APPROVED', 9002)
ON DUPLICATE KEY UPDATE
    status = 'APPROVED',
    updated_at = NOW(6);

INSERT INTO shops (
    id, created_at, updated_at, address, application_id, category, closed_days,
    description, image_urls, is_certified, lat, lng, operating_hours, phone,
    rating, review_count, shop_name, status, user_id, version, place_id
) VALUES
    (9501, NOW(6), NOW(6), '서울특별시 종로구 자하문로 15길 12', 9401, 'CAFE', '매주 월요일',
     '전통차, 약과, 계절 디저트를 즐길 수 있는 작은 한옥 카페입니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=1200'),
     b'1', 37.5808120, 126.9706420, '10:00-21:00', '02-1234-5678',
     4.7, 84, '춘배다방', 'ACTIVE', 9002, 0, 9101),
    (9502, NOW(6), NOW(6), '서울특별시 종로구 창경궁로 88 광장시장 2구역', 9402, 'FOOD', '연중무휴',
     '3대째 이어온 광장시장 대표 녹두 빈대떡 전문점. 고소하고 바삭한 맛이 일품입니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=1200'),
     b'1', 37.5700390, 126.9996030, '09:00-22:00', '02-2267-1111',
     4.8, 312, '순희네 빈대떡', 'ACTIVE', 9002, 0, 9102),
    (9503, NOW(6), NOW(6), '서울특별시 종로구 창경궁로 88 광장시장 1구역', 9403, 'FOOD', '연중무휴',
     '신선한 한우 육회와 바삭한 빈대떡으로 유명한 광장시장 맛집. 막걸리와 함께하면 더욱 맛있습니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=1200'),
     b'0', 37.5701200, 126.9994500, '09:00-22:00', '02-2267-2222',
     4.6, 198, '박가네 빈대떡+육회', 'ACTIVE', 9002, 0, 9102)
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    place_id = VALUES(place_id),
    updated_at = NOW(6);

INSERT INTO shop_wallets (
    id, created_at, updated_at, account_holder, account_number, balance, bank_name, shop_id
) VALUES
    (9501, NOW(6), NOW(6), '춘배상인', '110-123-456789', 320000, '신한은행', 9501),
    (9502, NOW(6), NOW(6), '춘배상인', '110-123-456789', 580000, '신한은행', 9502),
    (9503, NOW(6), NOW(6), '춘배상인', '110-123-456789', 430000, '신한은행', 9503)
ON DUPLICATE KEY UPDATE
    balance = VALUES(balance),
    updated_at = NOW(6);

INSERT INTO menus (
    id, created_at, updated_at, deleted_at, description, image_url, is_available,
    name, price, shop_id
) VALUES
    -- 춘배다방 (경복궁 인근)
    (9601, NOW(6), NOW(6), NULL, '구수한 향의 따뜻한 대추차', 'https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=600', b'1', '대추차', 6500, 9501),
    (9602, NOW(6), NOW(6), NULL, '겉은 바삭하고 속은 촉촉한 수제 약과', 'https://images.unsplash.com/photo-1488477181946-6428a0291777?w=600', b'1', '수제 약과', 4500, 9501),
    (9603, NOW(6), NOW(6), NULL, '계절 과일을 올린 빙수', 'https://images.unsplash.com/photo-1563805042-7684c019e1cb?w=600', b'1', '계절 빙수', 12000, 9501),
    -- 순희네 빈대떡 (광장시장)
    (9604, NOW(6), NOW(6), NULL, '국산 녹두로 만든 고소하고 바삭한 전통 빈대떡', 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600', b'1', '녹두 빈대떡', 7000, 9502),
    (9605, NOW(6), NOW(6), NULL, '녹두 빈대떡 2장 + 막걸리 1병 세트', 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600', b'1', '빈대떡 세트', 18000, 9502),
    (9606, NOW(6), NOW(6), NULL, '빈대떡과 찰떡궁합인 국산 막걸리', 'https://images.unsplash.com/photo-1584208124888-6c46b8c4e14a?w=600', b'1', '막걸리', 4000, 9502),
    -- 박가네 빈대떡+육회 (광장시장)
    (9607, NOW(6), NOW(6), NULL, '겉은 바삭하고 속은 촉촉한 빈대떡', 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=600', b'1', '빈대떡', 6500, 9503),
    (9608, NOW(6), NOW(6), NULL, '신선한 한우 육회, 참기름과 배 곁들임', 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=600', b'1', '한우 육회', 15000, 9503),
    (9609, NOW(6), NOW(6), NULL, '시원한 국산 막걸리', 'https://images.unsplash.com/photo-1584208124888-6c46b8c4e14a?w=600', b'1', '막걸리', 4000, 9503)
ON DUPLICATE KEY UPDATE
    price = VALUES(price),
    is_available = b'1',
    updated_at = NOW(6);

INSERT INTO qr_pay_requests (
    id, created_at, updated_at, amount, expired_at, menu_items, pay_request_id,
    pending_key, reject_reason, shop_id, status, user_id, completed_at
) VALUES
    (9701, NOW(6), NOW(6), 11000, DATE_ADD(NOW(6), INTERVAL 30 MINUTE),
     JSON_ARRAY(JSON_OBJECT('menuId', 9601, 'name', '대추차', 'quantity', 1, 'price', 6500),
                JSON_OBJECT('menuId', 9602, 'name', '수제 약과', 'quantity', 1, 'price', 4500)),
     '11111111-1111-1111-1111-111111111111', 'local-seed-pending-9001-9501', NULL, 9501, 'PENDING', 9001, NULL)
ON DUPLICATE KEY UPDATE
    expired_at = DATE_ADD(NOW(6), INTERVAL 30 MINUTE),
    status = 'PENDING',
    updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Store products and purchase history
-- ---------------------------------------------------------------------
INSERT INTO products (
    id, created_at, updated_at, category, description, image_urls, merchant_name,
    name, original_price, original_stock, price, status, stock, validity_days, max_per_person
) VALUES
    (9801, NOW(6), NOW(6), 'COUPON', '춘배다방 전 메뉴에 사용할 수 있는 1만원 금액권입니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=1200'), '춘배다방',
     '춘배다방 1만원권', 10000, 100, 9000, 'ON_SALE', 92, 30, 5),
    (9802, NOW(6), NOW(6), 'TICKET', '경복궁 근처 한복 대여 제휴권입니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1600323573764-7e4dc0079e4f?w=1200'), '북촌한복',
     '한복 대여 2시간권', 20000, 50, 15000, 'ON_SALE', 34, 14, 2),
    (9803, NOW(6), NOW(6), 'GOODS', '여행 기록용 춘배투어 한정 스티커 팩입니다.',
     JSON_ARRAY('https://images.unsplash.com/photo-1516414447565-b14be0adf13e?w=1200'), '춘배투어',
     '춘배 스티커 팩', 5000, 200, 3500, 'ON_SALE', 188, NULL, 10)
ON DUPLICATE KEY UPDATE
    stock = VALUES(stock),
    status = 'ON_SALE',
    updated_at = NOW(6);

INSERT INTO store_orders (
    id, created_at, updated_at, product_id, product_name, product_price, quantity,
    status, total_price, user_id
) VALUES
    (9901, NOW(6), NOW(6), 9801, '춘배다방 1만원권', 9000, 1, 'COMPLETED', 9000, 9001)
ON DUPLICATE KEY UPDATE
    status = 'COMPLETED',
    updated_at = NOW(6);

INSERT INTO user_items (
    id, created_at, updated_at, expires_at, order_id, product_id, product_name, status, user_id
) VALUES
    (9901, NOW(6), NOW(6), DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY), 9901, 9801, '춘배다방 1만원권', 'AVAILABLE', 9001)
ON DUPLICATE KEY UPDATE
    status = 'AVAILABLE',
    updated_at = NOW(6);

-- ---------------------------------------------------------------------
-- Community and chat
-- ---------------------------------------------------------------------
INSERT INTO free_posts (
    id, created_at, updated_at, author_id, content, deleted_at, status, title
) VALUES
    (10001, NOW(6), NOW(6), 9001, '이번 주말 경복궁 야간 관람 다녀오신 분 계신가요? 사진 포인트 추천 부탁드려요.', NULL, 'ACTIVE', '경복궁 야간 관람 팁 궁금해요'),
    (10002, NOW(6), NOW(6), 9004, '광장시장 먹거리 코스는 육회, 빈대떡, 꽈배기 순서가 좋았습니다.', NULL, 'ACTIVE', '광장시장 먹거리 코스 후기')
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    updated_at = NOW(6);

INSERT INTO free_post_images (post_id, image_url, sort_order) VALUES
    (10002, 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=900', 1)
ON DUPLICATE KEY UPDATE
    image_url = VALUES(image_url);

INSERT INTO companion_posts (
    id, created_at, updated_at, author_id, content, current_members, deleted_at,
    max_members, meeting_date, place_id, place_name, region, status, title
) VALUES
    (10101, NOW(6), NOW(6), 9001, '한복 입고 경복궁 둘러본 뒤 서촌 카페까지 같이 갈 분을 찾습니다.', 2, NULL,
     4, DATE_ADD(CURRENT_DATE, INTERVAL 5 DAY), 9101, '경복궁', '서울', 'ACTIVE', '경복궁 한복 산책 동행 구해요')
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    updated_at = NOW(6);

INSERT INTO comments (
    id, created_at, updated_at, author_id, content, deleted_at, post_id, post_type, status, parent_comment_id
) VALUES
    (10201, NOW(6), NOW(6), 9004, '해질 무렵 근정전 쪽이 사진 찍기 좋아요!', NULL, 10001, 'FREE', 'ACTIVE', NULL),
    (10202, NOW(6), NOW(6), 9001, '좋아요. 그 코스로 일정 짜볼게요.', NULL, 10001, 'FREE', 'ACTIVE', 10201)
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    updated_at = NOW(6);

INSERT INTO chat_rooms (
    id, created_at, updated_at, current_members, description, max_members,
    owner_id, post_id, status, title, version
) VALUES
    (10301, NOW(6), NOW(6), 2, '경복궁 동행 일정 조율방', 4, 9001, 10101, 'OPEN', '경복궁 한복 산책', 0)
ON DUPLICATE KEY UPDATE
    status = 'OPEN',
    updated_at = NOW(6);

INSERT INTO chat_room_members (
    id, created_at, updated_at, joined_at, left_at, member_state, user_id, chat_room_id
) VALUES
    (10301, NOW(6), NOW(6), NOW(6), NULL, 'OWNER_ACTIVE', 9001, 10301),
    (10302, NOW(6), NOW(6), NOW(6), NULL, 'MEMBER_ACTIVE', 9004, 10301)
ON DUPLICATE KEY UPDATE
    member_state = VALUES(member_state),
    updated_at = NOW(6);

INSERT INTO messages (
    id, created_at, updated_at, chat_room_id, content, file_name, file_size,
    file_url, message_type, sender_id, translate_lang, translated_content
) VALUES
    (10401, NOW(6), NOW(6), 10301, '안녕하세요! 토요일 오후 3시 괜찮으세요?', NULL, NULL, NULL, 'TEXT', 9001, NULL, NULL),
    (10402, NOW(6), NOW(6), 10301, '좋아요. 광화문역에서 만나요.', NULL, NULL, NULL, 'TEXT', 9004, 'en', 'Sounds good. Let us meet at Gwanghwamun Station.')
ON DUPLICATE KEY UPDATE
    updated_at = NOW(6);

INSERT INTO notifications (
    id, created_at, deleted_at, is_read, message, reference_id, reference_type,
    title, type, user_id
) VALUES
    (10501, NOW(6), NULL, b'0', '경복궁 한복 산책 동행방에 새 메시지가 도착했습니다.', 10301, 'CHAT_ROOM',
     '새 채팅 메시지', 'CHAT_MESSAGE', 9001)
ON DUPLICATE KEY UPDATE
    is_read = b'0',
    created_at = NOW(6);

SET FOREIGN_KEY_CHECKS = 1;
