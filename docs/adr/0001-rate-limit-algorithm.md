# ADR 0001 — Rate Limit 알고리즘 선택

> Status: Accepted
> Date: 2026-05-20
> Context: KAN-65 (Epic KAN-64 운영 보안 인프라 S1) Part 1/2

## Context

sa-docs/11 운영 보안 정책 §Rate Limit에 따라 회원가입/로그인 endpoint에 IP 기반 rate limit을 도입한다.
배포 전 필수 보안 인프라이며, 무차별 공격(brute force) 방어 + 자원 보호가 목적.

선택해야 할 사항:
1. **저장소**: Redis (이미 보유) vs RDB vs 인메모리 (분산 환경 미지원)
2. **알고리즘**: Fixed Window vs Sliding Window Log vs Token Bucket vs Leaky Bucket
3. **구현체**: 자체 Lua 스크립트 vs Bucket4j-Redis vs Resilience4j-Redis 등 라이브러리

## 결정

**Redis + Fixed Window + 자체 Lua 스크립트**.

## 근거

### 저장소 = Redis

- Refresh Token, Access Blacklist, 향후 캐시까지 이미 Redis 채택 → 추가 인프라 불필요
- 단일 노드 분산락(Redisson 도입됨, KAN-45)이 있어 멀티 인스턴스 배포에도 일관 동작
- TTL 기반 자동 정리로 메모리 부담 적음

### 알고리즘 = Fixed Window

| 알고리즘 | 정확도 | 메모리 | 구현 복잡도 |
|---|---|---|---|
| **Fixed Window** ⭐ | ★★ (window boundary 약점) | ★★★ (키당 카운터 1개) | ★★★ (INCR + EXPIRE) |
| Sliding Window Log | ★★★ | ★ (요청마다 timestamp 저장) | ★★ |
| Token Bucket | ★★★ | ★★ (last refill 추적) | ★ (Lua HMSET 복잡) |
| Leaky Bucket | ★★★ | ★★ | ★ |

선택 사유:
- **정책이 단순함**: 회원가입 3회/10분, 로그인 5회/분. Fixed Window 정확도로 충분
- **window boundary 약점은 실용적 무시 가능**: 예) 0:59에 5회 + 1:01에 5회 = 짧은 시간 10회 가능하지만 정책상 5회/분이 분당 평균이지 분 boundary에서 정확한 정족수 요구가 아님
- **운영 단순성**: 키당 카운터 1개로 메모리 관리 부담 최소
- **단순함 = 안정성**: Fixed Window는 오류 가능성이 가장 낮음
- 만약 향후 부정확성 문제가 운영에서 발견되면 Sliding Window 또는 Token Bucket으로 교체 (Epic B S3 부하 측정 결과 참조)

### 구현체 = 자체 Lua 스크립트

| 옵션 | 의존성 | 정확도 | 코드 베이스 일관성 |
|---|---|---|---|
| **자체 Lua** ⭐ | 없음 | Fixed Window 충분 | RefreshTokenStore Lua 패턴과 일관 |
| Bucket4j-Redis | `com.bucket4j:bucket4j-redis` 추가 | Token Bucket 정확 | 새로운 라이브러리 학습 비용 |
| Resilience4j-Redis | `io.github.resilience4j:resilience4j-redis` | Token Bucket | 동일 |

선택 사유:
- **의존성 최소화**: 외부 라이브러리 도입 없이 Spring Data Redis만으로 충분
- **S3 Refresh Token Rotation의 Lua atomic CAS 패턴과 일관**: 팀이 이미 익숙
- **5줄 Lua 스크립트**: 검증/디버깅 부담 미미
- Bucket4j 도입 시 라이브러리 자체의 CVE/유지보수 의존성 추가 — Querydsl(KAN-27)에서 본 것처럼 fork 분기 위험

## Trade-offs (수용한 부담)

- **Fixed Window 정확도 약점**: window boundary 짧은 시간 폭주 가능. 정책상 무시 가능하지만 운영 부하 측정 결과에 따라 재평가 (Epic B S3)
- **Token Bucket의 점진적 충전 미지원**: 사용자가 1분 채워 5회 호출 후 즉시 다른 4회를 보내려면 1분 대기 필요. 자연스럽지만 UX 측면에서 Token Bucket이 더 부드러움
- **자체 구현 → 검증 책임 본인**: Lua 스크립트의 정확성/원자성을 통합 테스트로 직접 검증해야 함 (PR에 동시성 테스트 포함)

## 후속 변경 트리거

다음 상황이면 알고리즘/구현체 재평가:
- 운영에서 window boundary 폭주가 실제 공격으로 악용되는 경우 → Sliding Window 또는 Token Bucket
- Token Bucket의 점진 충전이 UX 개선에 필요한 경우 → Lua HMSET 패턴 또는 Bucket4j 도입
- Rate Limit이 다른 도메인(API quota, 결제 등)으로 확장되며 정책 복잡도가 증가하는 경우 → Bucket4j 검토

## 참조

- sa-docs/11 운영 보안 정책 §Rate Limit
- docs/prd/KAN-23-s1-followup.md §5.2 (Rate limiting)
- KAN-65 PR 1/2 (Rate Limiter 인프라)
- KAN-65 PR 2/2 (Filter/Config 통합)
