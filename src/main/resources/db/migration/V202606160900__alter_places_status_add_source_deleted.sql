-- KAN-306: 관광지 원천삭제/운영자삭제 상태 분리.
-- 기존: 원천(TourAPI) showflag 삭제와 운영자 수동 삭제가 같은 DELETED를 공유 →
--       원천이 다시 노출(showflag=1)돼도 upsert가 status≠ACTIVE라 SKIP → 원천 삭제분 영구 사장.
-- 변경: SOURCE_DELETED(원천 삭제) 신설 — markDeleted는 ACTIVE→SOURCE_DELETED로 전이하고,
--       원천 재노출 시 upsert가 SOURCE_DELETED→ACTIVE로 부활시킨다. 운영자 DELETED/HIDDEN은 현행 보존(부활 안 함).
-- 데이터: 기존 DELETED 행은 원천/운영자 구분이 불가하므로 그대로 둔다(= 운영자 삭제로 간주, 부활 대상 아님).
--       이후 원천 삭제는 SOURCE_DELETED로 분리 기록된다.
ALTER TABLE `places`
    MODIFY `status` ENUM('ACTIVE', 'DELETED', 'HIDDEN', 'SOURCE_DELETED') NOT NULL;
