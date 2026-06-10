-- KAN-252: 사용자 PENDING QR 결제 직접 취소 지원.
-- qr_pay_requests.status ENUM에 CANCELLED 추가 (기존: PENDING/COMPLETED/REJECTED/EXPIRED).
ALTER TABLE qr_pay_requests
    MODIFY COLUMN status ENUM(
        'PENDING',
        'COMPLETED',
        'REJECTED',
        'EXPIRED',
        'CANCELLED'
    ) NOT NULL;
