-- KAN-306: 기존 DELETED 데이터 보정 백필 — '원천 삭제'였던 것만 SOURCE_DELETED로 분리.
--
-- 배경: KAN-221(markDeleted) 이후 원천(TourAPI) 삭제와 운영자 수동 삭제가 같은 DELETED를 공유.
--       V202606160900에서 SOURCE_DELETED를 신설했으나 기존 DELETED 행은 전부 운영자 삭제로 남아 부활 불가.
--
-- 분류 기준(감사로그 기반 정밀 분리 — admin_action_logs):
--   운영자 삭제 = PLACE_DELETE 액션 로그에 해당 place id(target_id) 기록 존재 → DELETED 유지(부활 안 함)
--   원천 삭제   = source='API_FETCH' AND status='DELETED' AND PLACE_DELETE 로그 없음 → SOURCE_DELETED 보정(재노출 시 부활)
--   ※ MANUAL(수동 등록) 장소는 원천 삭제 경로(markDeleted, external_id 매칭)에 해당 없음 → 대상 제외.
--
-- 한계: PLACE_DELETE 감사로깅 도입 이전에 운영자가 삭제한 API_FETCH 행이 있으면 오분류 가능(로그 갭).
--       운영 DB 적용 전 영향 행수(아래 SELECT와 동일 조건)를 점검 권장.
UPDATE places p
SET p.status = 'SOURCE_DELETED'
WHERE p.status = 'DELETED'
  AND p.source = 'API_FETCH'
  AND NOT EXISTS (
      SELECT 1 FROM admin_action_logs l
      WHERE l.action_type = 'PLACE_DELETE'
        AND l.target_id = p.id
  );
