-- 관광지 리뷰 테이블 생성
CREATE TABLE place_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(500) NOT NULL,
    image_urls JSON,
    status VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    
    -- 외래키 제약 (옵션: 프로젝트 규칙에 따라 생략 가능하지만 명시)
    CONSTRAINT fk_place_reviews_place FOREIGN KEY (place_id) REFERENCES places(id),
    CONSTRAINT fk_place_reviews_user FOREIGN KEY (user_id) REFERENCES users(id),

    -- 1인 1리뷰 (ACTIVE 상태) 중복 방지 (MySQL 5.7+ 가상 컬럼 지원 활용)
    active_status VARCHAR(10) GENERATED ALWAYS AS (IF(status = 'ACTIVE', 'ACTIVE', NULL)) VIRTUAL,
    CONSTRAINT uk_place_review_active UNIQUE (user_id, place_id, active_status)
);

-- 조회 성능 향상을 위한 인덱스
CREATE INDEX idx_place_reviews_place ON place_reviews(place_id);
CREATE INDEX idx_place_reviews_user ON place_reviews(user_id);
CREATE INDEX idx_place_reviews_created ON place_reviews(created_at);

-- 애플리케이션 레벨의 락과 함께 DB 레벨에서도 uk_place_review_active를 통해 ACTIVE 리뷰의 중복 생성을 원천 차단합니다.
