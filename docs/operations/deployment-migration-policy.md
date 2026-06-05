# 배포 시 DB 마이그레이션 정책 (KAN-226, 배포 S0)

> 무중단(blue/green) 배포에서 구·신 컨테이너가 **같은 운영 DB를 공유**하므로, 스키마 변경이 잘못 섞이면
> 트래픽을 받는 구 버전이 즉사한다. 본 정책으로 안전한 마이그레이션 배포를 강제한다.

## 1. 핵심 위험
- 모든 앱 컨테이너는 부팅 시 Flyway migrate를 수행한다(`application.yml` flyway 설정).
- blue/green cutover 동안 blue(구)와 green(신)이 동시에 같은 DB에 붙는다.
- 이때 **파괴적 변경**(DROP/RENAME COLUMN, NOT NULL 추가 등)이 적용되면, 아직 구 스키마를 기대하는 blue가
  사라진 컬럼을 SELECT → `SQLException` 폭발. Flyway는 down 마이그레이션이 없어 앱을 롤백해도 스키마는 forward로 남는다.
- 안티패턴 실사례: `V202606011745__alter_festivals_replace_geo_fields.sql` (ADD → backfill → **DROP COLUMN**을 한 배포에 포함).

## 2. 규칙 — Expand / Contract (필수)
파괴적 변경은 **두 배포로 분리**한다.
1. **Expand 배포**: 새 컬럼/테이블 ADD(nullable) + 앱이 신·구 양쪽 호환. 데이터 backfill. **DROP 금지.**
2. (앱이 구 컬럼 사용을 완전히 중단했는지 확인 — 한 배포 이상 경과)
3. **Contract 배포**: 더 이상 안 쓰는 구 컬럼 DROP/RENAME.

→ DROP/RENAME/NOT NULL-추가는 **절대 컬럼 도입과 같은 배포에 넣지 않는다.**

## 3. 마이그레이션 실행 위치 (S4/S5 적용)
- 마이그레이션을 **앱 부팅 내장이 아니라 배포 파이프라인 단독 step**으로 승격한다.
  - 배포 스크립트: `docker run --rm <image> <flyway migrate>` 1회 실행(또는 전용 migrate 컨테이너).
  - blue/green 두 앱 컨테이너는 `spring.flyway.enabled=false`(validate만)로 부팅.
- 효과: 두 컨테이너의 동시 migrate 경합(MySQL named lock) 제거, validate-거부 엣지 제거, 마이그레이션 실패 시 배포 자체 abort.

## 4. PR 체크리스트 (마이그레이션 포함 PR 필수 확인)
- [ ] 이 마이그레이션은 **blue/green 호환**인가? (구 버전이 변경 후 스키마에서 정상 동작하는가)
- [ ] DROP/RENAME/NOT NULL-추가가 컬럼 도입과 **분리된 배포**인가?
- [ ] 이미 머지된 마이그레이션 파일을 **수정하지 않았는가** (checksum 불일치 → `validate-on-migrate=true`로 전 환경 부팅 차단)
- [ ] 큰 변경(테이블 재작성)은 야간/저트래픽 window 배포로 합의했는가?

## 5. 운영 첫 배포
- 첫 배포는 blue/green이 아니라 **단순 배포(짧은 downtime)**로 baseline 안착 후, 2회차부터 blue/green.
- `baseline-on-migrate=true`는 "V1 적용 간주"만 하고 실제 스키마 일치를 검증하지 않으므로(drift 위험), 첫 배포 후 schema 정합 확인 권장.

## 참조
- 배포 Epic D(KAN-222), S0(KAN-226), S4 CD / S5 blue/green
- `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=20s` (본 슬라이스에서 적용 — 종료 시 진행 요청 보존)
