# 09. 구현 체크포인트

> 구현 중 부족한 설정이나 클래스를 발견하면, **사용자에게 반드시 확인받고** 진행한다.
>
> **형식**: "~~이 필요한데, 추가해도 될까요?"

---

## 필수 설정 클래스 체크리스트

구현 시작 전에 아래 항목들이 준비되어 있는지 확인한다.

---

## 1️⃣ Redis 설정

**필요한 이유**: 분산 락, 재고 가점유, Pub/Sub 구현

- [ ] `RedisConfig.java` 또는 `RedisConfiguration` 클래스 존재
- [ ] `StringRedisTemplate` 빈 등록
- [ ] `Redisson` 의존성 추가 (`spring-boot-starter-data-redis`)
- [ ] `RedissonClient` 빈 등록

**부족 시 확인 메시지**:
```
"분산 락 구현(Redisson)을 위해 RedisConfig에
RedissonClient 빈을 등록해야 하는데, 추가해도 될까요?"
```

**예상 코드**:
```java
@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        return Redisson.create(config);
    }
}
```

---

## 3️⃣ PG 사 (결제) 연동 설정

**필요한 이유**: 엽전 충전 시 실제 결제 처리

- [ ] PG 사 API 클라이언트 (Toss Payments, KakaoPay 등) 의존성
- [ ] `PaymentGatewayClient` 인터페이스 또는 추상 클래스
- [ ] PG 사 설정값 (`application.yml`에 apiKey, secretKey 등)
- [ ] 결제 콜백 검증 로직 (서명 검증)

**부족 시 확인 메시지**:
```
"PG 사 결제 연동을 위해 PaymentGatewayClient 인터페이스와
실제 구현체(예: TossPaymentClient)가 필요한데, 추가해도 될까요?"
```

**예상 구조**:
```java
// 인터페이스
public interface PaymentGatewayClient {
    PaymentResponse requestPayment(PaymentRequest request);
    boolean verifyCallback(String signature, String payload);
}

// 구현체
@Component
public class TossPaymentClient implements PaymentGatewayClient {
    // 구현
}
```

---

## 4️⃣ 멱등성 키 저장소 설정

**필요한 이유**: 중복 결제 방지

- [ ] Redis 또는 DB에 멱등성 키 저장 로직
- [ ] 멱등성 키 TTL 설정 (일반적으로 24시간)
- [ ] 중복 요청 감지 및 예외 처리

**부족 시 확인 메시지**:
```
"멱등성 키를 Redis에 저장하는 IdempotencyService가 필요한데,
추가해도 될까요?"
```

**예상 코드**:
```java
@Component
public class IdempotencyService {
    private final RedisTemplate<String, String> redisTemplate;

    public boolean isDuplicate(String idempotencyKey) {
        String key = "idempotency:" + idempotencyKey;
        return redisTemplate.hasKey(key);
    }

    public void recordKey(String idempotencyKey) {
        String key = "idempotency:" + idempotencyKey;
        redisTemplate.opsForValue().set(key, "processed", 24, TimeUnit.HOURS);
    }
}
```

---

## 5️⃣ 에러 응답 관련 설정

**필요한 이유**: 공통 예외 처리

- [ ] `BusinessException` 클래스 존재
- [ ] `GlobalExceptionHandler` 클래스 존재
- [ ] ErrorCode enum에 내 도메인 에러코드 등록됨

**확인할 사항**:
```
ErrorCode enum 확인:
- INSUFFICIENT_BALANCE 있는가?
- PRODUCT_SOLD_OUT 있는가?
- PURCHASE_PROCESSING 있는가?
- ... 기타 PAY/STORE/MERCHANT/SHOP 에러코드 있는가?
```

**부족 시 확인 메시지**:
```
"ErrorCode enum에 내 도메인 에러코드를 등록해야 하는데,
추가해도 될까요?"
```

---

## 6️⃣ 트랜잭션 설정

**필요한 이유**: 데이터 정합성 보장

- [ ] Service 레이어에 `@Transactional` 활성화
- [ ] 읽기 전용 메서드는 `@Transactional(readOnly = true)` 설정
- [ ] 분산 락이 필요한 메서드는 명시적 트랜잭션 경계 설정

**확인할 사항**:
```
@Service
public class PaymentService {
    @Transactional  // ← 이게 있는가?
    public void charge(ChargeRequest request) { }

    @Transactional(readOnly = true)  // ← 읽기 전용인가?
    public WalletResponse getWallet(Long userId) { }
}
```

---

## 7️⃣ S3 파일 업로드 설정 (영수증 이미지)

**필요한 이유**: 영수증 사진 저장

- [ ] AWS S3 의존성 (`spring-cloud-starter-aws-s3`)
- [ ] S3 설정 (`accessKey`, `secretKey`, `bucket`, `region`)
- [ ] `S3UploadService` 또는 유사 클래스
- [ ] 파일명 UUID 변환 로직

**부족 시 확인 메시지**:
```
"S3 파일 업로드를 위해 S3UploadService가 필요한데,
추가해도 될까요?"
```

---

## 8️⃣ 로깅 설정

**필요한 이유**: 디버깅 및 모니터링

- [ ] SLF4J + Logback 설정 (`application.yml`)
- [ ] 민감 정보 (비밀번호, 토큰) 로깅 금지 규칙
- [ ] 결제/동시성 관련 로그는 DEBUG 또는 INFO 레벨

**확인할 사항**:
```yaml
logging:
  level:
    com.chunbaetour.payment: DEBUG
    com.chunbaetour.store: DEBUG
```

---

## 구현 중 발견한 부족사항 기록

| 기능 | 부족한 설정 | 확인 여부 | 추가 여부 |
|------|-----------|---------|---------|
| 엽전 충전 | RedissonClient 빈 | ⏳ | - |
| 상품 구매 | S3UploadService | ⏳ | - |
| QR 결제 | PG 사 콜백 검증 | ⏳ | - |

---

## 체크포인트 확인 프로세스

### 새 기능 구현 시작 시

1. **CLAUDE.md의 문서 인덱스 확인** (`06-DOC-INDEX.md`)
2. **이 파일 (`09-IMPLEMENTATION-CHECKLIST.md`)을 스캔**
3. **필요한 설정이 있는지 확인**
4. **부족하면 사용자에게 명시적으로 확인**

### 사용자에게 확인하는 예시

```
"엽전 충전 기능을 구현하려면 다음이 필요합니다:

1. RedissonClient 빈 (분산 락용)
   - RedisConfig에 등록 필요

2. PG 사 PaymentGatewayClient
   - Toss Payments / KakaoPay 클라이언트 필요

이 둘을 추가해도 될까요?
또는 이미 준비되어 있나요?"
```

---

## 주의사항

- **이 파일은 체크리스트일 뿐**, 모든 항목이 반드시 필요한 것은 아니다
- **프로젝트 상황에 따라 일부는 이미 준비**되어 있을 수 있다
- **새로운 부족사항을 발견하면 위 테이블에 기록**한다
