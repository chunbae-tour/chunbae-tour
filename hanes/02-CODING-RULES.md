# 02. 코딩 규칙

## 기술 스택
- Java 21, Spring Boot 4.0.6
- Spring Data JPA + QueryDSL
- MySQL 8.4 (RDS)
- Redis (Redisson 분산 락, String/ZSet)
- JWT + Spring Security

---

## 공통 응답 형식

모든 API 응답은 팀에서 이미 구현한 `ApiResponse` Record를 사용한다.

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "OK", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>("SUCCESS", "OK", null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
```

### 실제 응답 형식

**성공 (HTTP 200)**
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {}
}
```

**실패 (HTTP 400/403 등)**
```json
{
  "code": "PAY_001",
  "message": "엽전 잔액이 부족합니다."
}
```

> `@JsonInclude(NON_NULL)` 덕분에 실패 응답에서 `data`는 자동으로 제거된다.

### 사용 방법

**Service에서:**
```java
throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
```

**GlobalExceptionHandler가 자동으로 처리:**
```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
    ErrorCode code = ex.getErrorCode();
    return ResponseEntity.status(code.getStatus())
            .body(ApiResponse.error(code.getCode(), code.getMessage()));
}
```

개발자는 **ErrorCode Enum을 던지기만 하면**, 나머지는 GlobalExceptionHandler가 자동으로 code와 message를 추출해서 응답을 만든다.

---

## 공통 유틸리티 (domain/common) — 반드시 사용

> `domain/common` 패키지에 팀 공통 유틸/응답/에러 클래스가 있다.
> **직접 구현 금지** — 아래 목록 먼저 확인 후 가져다 쓴다.

| 클래스 | 위치 | 용도 |
|--------|------|------|
| `CursorUtils` | `domain/common/util/CursorUtils.java` | cursor Base64URL 인코딩/디코딩 |
| `CursorPageResponse<T>` | `domain/common/response/CursorPageResponse.java` | cursor 페이징 응답 래퍼 |
| `ApiResponse<T>` | `domain/common/response/ApiResponse.java` | 공통 API 응답 형식 |
| `BusinessException` | `domain/common/error/BusinessException.java` | 비즈니스 예외 |
| `ErrorCode` | `domain/common/error/ErrorCode.java` | 에러코드 Enum |

### CursorUtils 사용법

```java
// 인코딩 (id → cursor 문자열)
String cursor = CursorUtils.encode(entity.getId());

// 디코딩 (cursor 문자열 → id)
// IllegalArgumentException 던짐 → INVALID_CURSOR로 변환 필요
try {
    long id = CursorUtils.decode(cursor);
} catch (IllegalArgumentException e) {
    throw new BusinessException(ErrorCode.INVALID_CURSOR);
}
```

---

## 페이징 방식

**Cursor 기반 페이징**을 사용한다 (Offset 페이징 금지).

```java
// Request
cursor: String (Base64)
size: int

// Response
content: List<T>
nextCursor: String
hasNext: boolean
size: int
```

---

## 예외 처리 규칙

에러코드는 반드시 `ErrorCode` Enum 값을 사용한다. **String이 아닌 Enum 상수를 던진다.**

```java
// ✅ 올바른 방식 (Enum 상수)
throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);

// ❌ 잘못된 방식 (String 직접 입력)
throw new BusinessException("PAY_001", "엽전 잔액이 부족합니다.");
throw new BusinessException(ErrorCode.PAY_001); // PAY_001은 Enum 상수가 아님

// 도메인별 커스텀 예외도 허용
throw new PaymentException(ErrorCode.INSUFFICIENT_BALANCE);
```

### Enum 상수 목록

내 도메인에서 사용할 Enum 상수:

| Enum 상수 | Code | Message |
|-----------|------|---------|
| `INSUFFICIENT_BALANCE` | PAY_001 | 엽전 잔액이 부족합니다. |
| `CHARGE_AMOUNT_TOO_LOW` | PAY_002 | 충전 금액은 1,000원 이상이어야 합니다. |
| `PRODUCT_SOLD_OUT` | STORE_002 | 품절된 상품입니다. |
| `PURCHASE_PROCESSING` | STORE_005 | 구매 처리 중입니다. |
| ... | ... | ... |

> **중요**: `ErrorCode.PAY_001` 같은 방식은 사용하지 않는다.
> Enum 상수는 대문자 언더스코어 형식이어야 한다: `INSUFFICIENT_BALANCE`

---

## 코딩 컨벤션

- 메서드 단위로 작업 (클래스 전체 재작성 금지)
- 기존 코드 스타일 유지
- SQL 직접 작성 금지 → JPA / QueryDSL 사용
- 비밀번호, 토큰 등 민감 정보 로그 출력 금지
- 트랜잭션 경계는 Service 레이어에서 관리
- Repository에서 비즈니스 로직 금지

---

## 주석 작성 규칙

> 목적: 팀원이 코드를 읽을 때 **이 코드가 무엇을 하는지** 바로 파악할 수 있도록.

### 클래스 레벨 Javadoc (`/** */`)

Service / Controller / Repository 클래스 상단에 작성.

```java
/**
 * 엽전 지갑(Wallet) 서비스.
 * 담당 기능: 잔액 조회, PG 결제 완료 후 엽전 충전, 신규 유저 지갑 생성.
 * charge()는 CallbackService(PG 웹훅)에서 호출되며 SELECT FOR UPDATE로 정합성 보장.
 */
```

### 메서드 레벨 주석 (`/** */` 또는 `//`)

public 메서드에 한 줄 요약 + 핵심 동작/예외 명시.

```java
/** 내 엽전 잔액 조회. 지갑 없으면 PAY_012(WALLET_NOT_FOUND) 반환. */
public WalletBalanceResponse getWallet(Long userId) { ... }
```

### 인라인 주석 (`//`)

각 핵심 라인 위에 **이 라인이 무슨 동작을 하는지** 한 줄로 설명.

```java
// DB에서 유저 지갑 조회 (없으면 예외)
Wallet wallet = walletRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

// 잔액 증가
wallet.credit(amount);

// 충전 이력 DB 저장 (balanceSnapshot = credit 후 잔액)
yeopjeonHistoryRepository.save(...);
```

### 하드코딩 금지

문자열 리터럴(제약명, 키 이름 등)은 반드시 상수로 추출.

```java
// ❌ 금지
if ("uk_wallets_user_id".equalsIgnoreCase(cve.getConstraintName()))

// ✅ 올바른 방식
private static final String UK_WALLETS_USER_ID = "uk_wallets_user_id";
if (UK_WALLETS_USER_ID.equalsIgnoreCase(cve.getConstraintName()))
```

### API 경로가 명세와 다를 경우

컨트롤러 클래스 Javadoc 또는 메서드 주석에 명시.

```java
/** 내 엽전 잔액 조회 (명세: GET /wallets/me → 구현: GET /yeopjeon/balance, 명시성 위해 변경) */
```

---

## 멱등성 키 처리

결제/충전 API는 반드시 멱등성 키를 처리한다.

```java
// Header에서 추출
@RequestHeader("Idempotency-Key") String idempotencyKey

// Redis에서 중복 확인
String key = "idempotency:" + idempotencyKey;
if (redisTemplate.hasKey(key)) {
    throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
}
redisTemplate.opsForValue().set(key, "processed", 24, TimeUnit.HOURS);
```

---

## Rate Limiting (보안 정책 준수)

결제 관련 API는 Rate Limit이 적용된다.
- 결제 요청: userId 기준 5회 / 1분 → 초과 시 `COMMON_006`

---

## 파일 업로드

- 영수증 이미지: 필수, 최대 5MB, jpg/jpeg/png/webp
- S3 업로드 후 URL 저장
- 파일명은 UUID 기반으로 변경 저장

## 엔티티 설계 패턴

### 엔티티 클래스

```java
@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long balance;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Builder 생성자 (protected)
    @Builder
    private Wallet(Long userId, Long balance) {
        this.userId = userId;
        this.balance = balance;
    }

    // 정적 팩토리 메서드
    public static Wallet create(Long userId) {
        return Wallet.builder()
                .userId(userId)
                .balance(0L)
                .build();
    }

    // 비즈니스 로직
    public void charge(Long amount) {
        this.balance += amount;
    }

    public void pay(Long amount) {
        if (this.balance < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }
}
```

**특징:**
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`: 외부에서 `new` 생성자 호출 금지
- `@Builder`: Builder 패턴으로 생성
- 정적 팩토리 메서드 (`create`, `of` 등): 비즈니스 의도를 명확히 표현
- 비즈니스 로직은 엔티티에 포함

---

## DTO / Response 레코드 패턴

### Record 사용 (불변 객체)

```java
public record WalletResponse(
        Long walletId,
        Long userId,
        Long balance,
        LocalDateTime createdAt
) {
    // from: Entity → DTO 변환
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCreatedAt()
        );
    }

    // of: 여러 Entity → DTO 조합
    public static WalletResponse of(Wallet wallet, User user) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCreatedAt()
        );
    }
}
```

### Request Record

```java
public record ChargeRequest(
        Long amount
) {
    // 빌더 없음 (불변 레코드)
}
```

---

## 팩토리 메서드 네이밍 규칙

| 메서드 | 용도 | 예시 |
|--------|------|------|
| `create` | 엔티티 생성 (기본값 포함) | `Wallet.create(userId)` |
| `of` | 여러 객체 조합 생성 | `PaymentResponse.of(order, wallet)` |
| `from` | Entity/Domain → DTO 변환 | `WalletResponse.from(wallet)` |

---

## 생성자 규칙

- **엔티티**: `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + Builder
- **DTO/Response**: Record 사용 (생성자 자동 생성)
- **Request**: Record 사용

외부에서 직접 생성자 호출 금지 → 항상 팩토리 메서드 또는 Builder 사용

---

## Swagger(OpenAPI) 어노테이션 규칙 (필수)

모든 컨트롤러에 `@Tag`, 모든 public 엔드포인트에 `@Operation`을 작성한다.

### 컨트롤러 클래스 — `@Tag`

```java
@Tag(name = "엽전", description = "엽전 잔액·사용 내역 조회 (/api/v1/yeopjeon/**)")
@RestController
@RequestMapping("/api/v1/yeopjeon")
public class YeopjeonController { ... }
```

- `name`: 도메인 한글명 (예: "엽전", "결제", "스토어")
- `description`: 담당 기능 한 줄 요약 + 기본 경로

### 엔드포인트 메서드 — `@Operation`

```java
@Operation(summary = "엽전 잔액 조회")
@GetMapping("/balance")
public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(...) { ... }
```

- `summary`: 메서드가 하는 일을 명사형 한글 한 줄 (예: "엽전 잔액 조회", "QR 결제 요청")
- 상세 설명이 필요하면 `description` 추가 (선택)

---

## 패키지 구조 규칙 (필수)

> **새 클래스 생성 전 반드시 `docs/13_백엔드_패키지_구조_설계서.md`를 확인한다.**

### 도메인 서브패키지 구조

```
domain/{도메인명}/
  ├── controller/     ← REST 컨트롤러
  ├── service/        ← 비즈니스 로직
  ├── repository/     ← JPA Repository
  ├── entity/         ← JPA 엔티티
  ├── dto/
  │   ├── request/    ← 요청 DTO (Record)
  │   └── response/   ← 응답 DTO (Record)
  └── type/           ← Enum 타입
```

### 도메인 → 패키지 매핑

| 기능 | 패키지 |
|------|--------|
| 지갑(엽전) | `domain.yeopjeon` |
| 결제 흐름 | `domain.payment` |
| 상점 | `domain.store` |
| 상인 | `domain.merchant` |
| 공지/운영 | `domain.shop` |

### DTO 네이밍 규칙

- Response DTO: `{기능}Response` (예: `WalletBalanceResponse`)
- Controller명: 도메인 단위 (예: `YeopjeonController`, `StoreController`)
- 응답 DTO 위치: `dto/response/` (절대 `dto/` 바로 아래 금지)
- 요청 DTO 위치: `dto/request/`
