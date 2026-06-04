-- KAN-209: ad_applications (광고 신청) 테이블 생성
CREATE TABLE ad_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    ad_type VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    cost BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reject_reason VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,

    INDEX idx_ad_applications_shop_id (shop_id),
    INDEX idx_ad_applications_shop_id_status (shop_id, status),
    INDEX idx_ad_applications_status_id (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
