# ADR 0003 — 환불 처리 구조: 동기 직접 호출 → 비동기 스케줄러 전환

> Status: Accepted
> Date: 2026-06-02
> Context: KAN-205 (PortOne 환불 취소 요청 멱등성 및 재시도 구조 개선)

## Context

기존 `RefundService.requestRefund()`는 사용자 환불 요청 시 PortOne `cancelPayment()`를 **동기로 직접 호출**했다.

이 구조에서 발생한 문제:

1. **정산 불일치**: `cancelPayment()` 타임아웃 → 서버는 예외 수신, PortOne은 취소 완료.
   DB는 FAILED, PG는 환불됨. 재시도 시 이미 취소된 결제에 2차 취소 요청 가능.

2. **부분환불 기록 없음**: 잔액 부족(부분 사용) 시 예외만 던지고 이력이 남지 않아
   사용자가 왜 거절됐는지 확인 불가.

3. **재시도 구조 부재**: PG 일시 장애로 실패한 환불 건을 자동으로 재시도하는 메커니즘 없음.
   운영자가 수동으로 확인해야 함.

4. **API 응답 지연**: 사용자 요청 스레드에서 외부 PG 호출을 기다려야 해 응답 지연 발생.

## 결정

**환불 처리를 비동기 스케줄러 기반으로 전환**하고, **전액환불 정책**을 명시적으로 적용한다.

## 변경 전 플로우

```
사용자 환불 요청
  → requestRefund()
      → prepareRefund() (PENDING 저장)
      → cancelPayment() (PG 동기 직접 호출)
          성공 → completeRefundAfterGatewayCancel() → APPROVED
          실패 → markRefundFailed() → FAILED (재시도 없음)
```

## 변경 후 플로우

```
사용자 환불 요청
  → requestRefund()
      잔액 < 충전액 (부분 사용) → REJECTED 저장 ("부분환불은 불가합니다") → 에러 반환
      잔액 == 충전액            → PENDING 저장 → 즉시 응답 반환

스케줄러 (1분마다)
  → processPendingRefunds()
      → cancelPayment() (PG 호출)
          성공 → completeSchedulerRetry() → APPROVED, 엽전 회수, 주문 REFUNDED
          실패 → recordFailure() → FAILED (지수 백오프 재시도 예약)

재시도 스케줄러 (1분마다, next_retry_at 도래 건만)
  → retryFailedRefunds()
      → cancelPayment() 재시도 (최대 5회, 지수 백오프: 1→5→15→60→240분)
          성공 → completeSchedulerRetry() → APPROVED, 엽전 회수, 주문 REFUNDED
          5회 모두 실패 → REQUIRES_ADMIN (관리자 직접 처리)
```

## 주요 정책 결정

### 1. 전액환불만 허용

충전 금액 전액이 지갑에 남아있는 경우에만 환불 가능.
부분 사용 후 환불은 정책상 불가 → REJECTED 이력 저장으로 사용자에게 이유 제공.

**근거**: 엽전은 소모성 재화. 일부 소비 후 차액 환불은 정산 복잡도를 과도하게 높임.

### 2. PG 직접 호출 제거 (requestRefund)

API 응답에서 외부 PG 호출을 분리해 응답 속도와 장애 격리를 보장.
PG 장애가 사용자 환불 요청 API 응답에 영향을 주지 않음.

### 3. refundId 기반 Idempotency-Key

`cancelPayment()` 호출 시 `Idempotency-Key: refund-{refundId}` 헤더 전달.
동일 환불 건 재시도 시 PortOne이 중복 취소 없이 기존 결과 반환.

### 4. 지수 백오프 재시도 정책

```
PENDING 첫 실패  → 1분 후 재시도  (충전 직후 실수, 빠른 환불 기대 대응)
FAILED 1회       → 5분 후
FAILED 2회       → 15분 후
FAILED 3회       → 60분 후
FAILED 4회       → 240분 후
FAILED 5회 초과  → REQUIRES_ADMIN (자동 처리 불가)
```

**근거**: 단일 간격 재시도는 PG 장애 상황에서 동시 트래픽 폭발(thundering herd)을 유발.
간격을 늘릴수록 "일시 장애" vs "심각한 장애" 구분이 명확해져 불필요한 PG 호출 감소.

### 5. 다중 인스턴스 중복 실행 방지 — ShedLock

`@Scheduled`는 인스턴스마다 독립 실행되므로 2대 이상 배포 시 동일 PENDING/FAILED 건에 대해
`cancelPayment()`가 중복 호출될 수 있다.

**ShedLock** 도입으로 `processPendingRefunds`, `retryFailedRefunds` 각각에 분산 락을 적용.
공유 DB의 `shedlock` 테이블을 통해 한 인스턴스만 실행. `usingDbTime()`으로 인스턴스 간 시계 편차 무시.

```
lockAtMostFor = PT3M   # 최대 락 유지 시간 (BATCH_SIZE × PG 호출 최대치 고려)
lockAtLeastFor = PT30S # 실행 직후 즉시 재실행 방지
```

Idempotency-Key(`refund-{refundId}`)로 PG 이중 취소도 방어하지만, ShedLock이 불필요한 외부 호출 자체를 차단.

### 6. REQUIRES_ADMIN 처리 경로

5회 재시도 소진 → REQUIRES_ADMIN 전환. 관리자가 `AdminRefundService.approveRefund()`·`rejectRefund()`로 수동 해소 가능.

- `approve()`: PENDING | REQUIRES_ADMIN 허용 → PG 재시도 후 APPROVED 전환
- `reject()`: PENDING | REQUIRES_ADMIN 허용 → 환불 거절 처리

### 7. 락 획득 순서 통일 — 데드락 방지

admin `approveRefund`와 스케줄러 `completeSchedulerRetry` 모두 **Refund → Order** 순서로 잠금.
역전 시 교차 대기로 데드락 발생 가능.

### 8. 취소-스케줄러 경합 처리

`cancelPayment()` 성공 ~ `completeSchedulerRetry()` 트랜잭션 사이에 사용자가
`cancelRefund()`로 PENDING→CANCELLED 전환 가능.

이 경우 PG에서 이미 돈이 반환됐으므로 CANCELLED 상태여도 엽전 회수 + 주문 전환 + APPROVED 확정.
비관적 락(`findByIdWithLock`)으로 동시 처리를 직렬화해 이중 회수 방지.

### 6. RefundStatus 추가

| 상태 | 의미 |
|------|------|
| PENDING | 환불 요청 접수, 스케줄러 처리 대기 |
| APPROVED | PG 취소 성공, 엽전 회수 완료 |
| REJECTED | 부분 사용 등 정책상 거절, 이력 보존 |
| CANCELLED | 사용자가 PG 호출 전 철회 |
| FAILED | PG 취소 실패, 재시도 스케줄러 대기 |
| REQUIRES_ADMIN | 재시도 횟수 초과, 관리자 직접 처리 필요 |

## 트레이드오프

| 항목 | 기존 (동기) | 변경 (비동기) |
|------|------------|--------------|
| 환불 처리 속도 | 즉시 (PG 응답 대기) | 최대 1분 지연 |
| PG 장애 영향 | API 응답 직접 영향 | API 응답 무관 |
| 정산 불일치 위험 | 타임아웃 시 발생 | Idempotency-Key + verifyPayment 가드로 방어 |
| 운영 복잡도 | 단순 | 스케줄러 + 상태 관리 추가 |

## 영향 범위

- `RefundService.requestRefund()` 반환값: APPROVED → PENDING (사용자 응답 변경)
- `refunds` 테이블: `retry_count`, `next_retry_at` 컬럼 추가 (V202606021200)
- `refunds.status` enum: `REQUIRES_ADMIN` 추가 (V202606021430)
- `AdminRefundService.approveRefund()`, `rejectRefund()`: REQUIRES_ADMIN 상태도 처리 가능하도록 확장
- `shedlock` 테이블 추가 (V202606022110) — 다중 인스턴스 분산 락
- `build.gradle`: `shedlock-spring`, `shedlock-provider-jdbc-template` 의존성 추가
- 락 순서 통일: `completeSchedulerRetry` Refund→Order (기존 Order→Refund 역전 수정)
