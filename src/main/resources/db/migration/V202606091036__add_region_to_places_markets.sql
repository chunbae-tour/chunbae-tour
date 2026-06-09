-- KAN-249: 지역 기반 검색을 위해 관광지/전통시장에 행정구역(시도/시군구) 정규화 컬럼 추가.
-- place는 TourAPI areacode 매핑, market은 도로명주소 파싱으로 수집 시 채운다.
-- 외부 데이터 누락 가능성으로 nullable. 지역 필터 range scan용 (sido, sigungu) 복합 인덱스.

ALTER TABLE places
    ADD COLUMN sido    VARCHAR(20) NULL,
    ADD COLUMN sigungu VARCHAR(30) NULL;

CREATE INDEX idx_places_region ON places (sido, sigungu);

ALTER TABLE traditional_markets
    ADD COLUMN sido    VARCHAR(20) NULL,
    ADD COLUMN sigungu VARCHAR(30) NULL;

CREATE INDEX idx_traditional_markets_region ON traditional_markets (sido, sigungu);
