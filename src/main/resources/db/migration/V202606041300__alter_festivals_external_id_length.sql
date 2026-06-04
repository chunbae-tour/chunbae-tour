-- externalId 형식 변경: contentid(숫자) → insttCode_fstvlNm(최대 ~80자)
ALTER TABLE festivals MODIFY COLUMN external_id VARCHAR(100);
