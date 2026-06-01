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
    CONSTRAINT fk_place_reviews_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 조회 성능 향상을 위한 인덱스
CREATE INDEX idx_place_reviews_place ON place_reviews(place_id);
CREATE INDEX idx_place_reviews_user ON place_reviews(user_id);
CREATE INDEX idx_place_reviews_created ON place_reviews(created_at);

-- 유니크 제약은 삭제 후 재작성(Soft Delete)을 지원하기 위해 DB 레벨에서는 제외하고 
-- 애플리케이션 레벨의 비관적 락(Place 잠금)과 exists 조회로 동시성/중복을 제어합니다.
