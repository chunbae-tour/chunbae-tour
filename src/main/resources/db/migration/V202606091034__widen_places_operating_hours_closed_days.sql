-- KAN-221 Tier-2: 상세 수집(KorService2 detailIntro2)의 운영시간(usetime)·휴무일(restdate) 문자열이
-- 기존 VARCHAR(100)을 초과한다(예: 경복궁 usetime 200자+). 잘림 방지를 위해 500으로 확장.
ALTER TABLE places
    MODIFY COLUMN operating_hours VARCHAR(500),
    MODIFY COLUMN closed_days     VARCHAR(500);
