-- 상담 채팅 파일/이미지 업로드(KAN-309 CS) — IMAGE/FILE 메시지 표시용 컬럼 추가 (messages 테이블 동일 컬럼 참고)
ALTER TABLE `support_messages`
  ADD COLUMN `file_name` varchar(255) DEFAULT NULL AFTER `file_url`,
  ADD COLUMN `file_size` bigint       DEFAULT NULL AFTER `file_name`;
