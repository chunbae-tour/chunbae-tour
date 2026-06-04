# 10. 영수증 이미지 관리 가이드

> 관광지 리뷰 작성 시 영수증 사진이 필요한 이유와 구현 방법을 정리한 문서.

---

## 1. 왜 신현민이 맡아야 하는가?

### 도메인 책임 분담

```
박경화 (리뷰 도메인)
  ├─ 리뷰 작성 로직 (별점, 텍스트, 일반 사진)
  ├─ 리뷰 CRUD
  └─ 리뷰 관리

신현민 (결제/영수증 도메인) ← 영수증은 여기!
  ├─ 엽전 충전 / 결제
  ├─ 결제 내역 관리
  ├─ 환불 처리
  └─ 결제 증빙자료 (영수증) ← 여기!
```

### 영수증이 신현민 도메인인 이유

**영수증 = 결제 증빙자료**

```
손님이 가게에서 밥을 먹음
  ↓
결제 (신현민의 일)
  ├─ 엽전 차감
  ├─ 결제 내역 기록
  └─ 영수증 발급 ← 결제와 함께 생기는 자료
  
며칠 후, 리뷰를 남기려고 함
  ↓
"이 리뷰가 진짜 방문한 거야?" 증명 필요
  ↓
결제 시 받은 영수증을 제출
  ↓
신현민: "오케이, 이 영수증 관리해줄게"
```

**핵심**: 영수증은 "리뷰의 첨부 파일"이 아니라 "결제의 증거"

---

## 2. 영수증 검증 체계 (3단계)

### 📋 현재 MVP (1단계만 구현)

```
사용자가 영수증 사진 제출
  ↓
[1단계] 파일 형식 검증 ← MVP에서 구현
  ├─ 파일 존재 확인
  ├─ 파일 크기 (최대 5MB)
  └─ 파일 포맷 (jpg, jpeg, png, webp)
  ↓
  ✅ OK → S3 저장
  ❌ FAIL → 에러 반환
```

### 🔄 추후 확장 (2~3단계 추가)

```
[2단계] 결제 이력 매칭 (1차 개발)
  ├─ payment_orders 테이블에서 같은 금액 결제 찾기
  ├─ 결제 날짜와 리뷰 작성 날짜 비교
  └─ "이 영수증이 실제 결제와 맞는가?" 확인

[3단계] AI/OCR 검증 (2차 이상)
  ├─ OCR로 영수증에서 가게명, 금액 추출
  ├─ 리뷰하려는 가게와 일치 확인
  └─ 머신러닝으로 위조 영수증 탐지
```

---

## 3. 구현해야 하는 것들

### MVP (1차 개발 - 지금)

#### 3-1. 에러코드 추가

**ErrorCode enum에 추가:**

```java
public enum ErrorCode {
    // ... 기존 코드 ...
    
    // ===== 파일 업로드 (공통) =====
    RECEIPT_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "FILE_001", "영수증 사진은 필수입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_002", "파일 크기가 최대 5MB를 초과했습니다."),
    INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "FILE_003", "허용되지 않는 파일 형식입니다. (jpg, jpeg, png, webp만)"),
}
```

#### 3-2. 영수증 검증 로직

**ReviewService에 추가:**

```java
@Service
public class ReviewService {
    private final S3UploadService s3UploadService;
    
    /**
     * 영수증 파일 검증 (형식만)
     * MVP: 파일 크기, 포맷만 검증
     * 추후: 결제 이력 매칭, OCR 등 추가
     */
    private void validateReceiptImage(MultipartFile receiptImage) {
        // 1. 파일 존재 확인
        if (receiptImage == null || receiptImage.isEmpty()) {
            throw new BusinessException(ErrorCode.RECEIPT_IMAGE_REQUIRED);
        }
        
        // 2. 파일 크기 확인 (최대 5MB)
        if (receiptImage.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        
        // 3. 파일 포맷 확인 (jpg, jpeg, png, webp만)
        String filename = receiptImage.getOriginalFilename();
        if (!filename.matches(".*\\.(jpg|jpeg|png|webp)$")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        
        // MVP: 이 정도만 검증!
        // 실제 영수증인지는 나중에 관리자 신고 시 확인
    }
    
    @Transactional
    public ReviewResponse createReview(Long placeId, ReviewCreateRequest request) {
        // 1. 영수증 검증
        validateReceiptImage(request.receiptImage());
        
        // 2. 영수증 S3 업로드
        String receiptImageUrl = s3UploadService.uploadFile(
            request.receiptImage(),
            "receipts"  // S3 폴더명
        );
        
        // 3. 일반 이미지 처리는 박경화가 담당
        // (ReviewService 내에서 하거나 별도 로직)
        
        // 4. 리뷰 저장
        Review review = Review.builder()
            .placeId(placeId)
            .userId(getCurrentUserId())
            .rating(request.rating())
            .content(request.content())
            .receiptImageUrl(receiptImageUrl)  // ← 영수증 URL
            .build();
        
        return ReviewResponse.from(reviewRepository.save(review));
    }
}
```

#### 3-3. Request DTO 수정

```java
public record ReviewCreateRequest(
    int rating,
    String content,
    List<MultipartFile> images,        // 일반 사진 (박경화 담당)
    MultipartFile receiptImage         // 영수증 사진 (신현민 담당)
) {}
```

#### 3-4. Entity에 영수증 URL 필드 추가

```java
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long placeId;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private int rating;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(length = 500)
    private String receiptImageUrl;  // ← 여기 추가!
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

---

## 4. MVP에서 하는 것 (지금)

### ✅ 구현 범위

| 항목 | 범위 | 예시 |
|------|------|------|
| 파일 크기 검증 | ✅ | 최대 5MB |
| 파일 포맷 검증 | ✅ | jpg, jpeg, png, webp |
| S3 업로드 | ✅ | UUID 기반 파일명 |
| URL 저장 | ✅ | receiptImageUrl DB 저장 |
| 결제 이력 매칭 | ❌ | 추후 확장 |
| OCR 인식 | ❌ | 추후 확장 |
| 위조 탐지 | ❌ | 추후 확장 |

### ❌ 하지 않는 것

```
❌ "이 영수증이 정말 그 가게의 영수증인가?" 검증
❌ "이 영수증이 실제로 존재하는 가게인가?" 검증
❌ 자동으로 가짜 영수증 탐지
```

### 🔒 가짜 영수증 방지 방법 (현재)

```
1. 사용자가 가짜 영수증으로 리뷰를 남김
   ↓
2. 다른 사용자가 "이 리뷰 영수증이 가짜다"고 신고
   (박경화 도메인 - 신고 기능)
   ↓
3. 관리자가 신고 내용 확인
   ↓
4. 관리자가 리뷰 삭제
   (정민교 도메인 - 관리자 기능)
```

---

## 5. 추후 확장 (2차 개발 이후)

### Phase 1: 결제 이력 매칭

```java
/**
 * 추후 추가: 결제 이력과 영수증 매칭
 * 요구사항: payment_orders 테이블 접근 필요
 */
private void validateReceiptWithPaymentHistory(
    Long userId, 
    Long placeId, 
    MultipartFile receiptImage
) {
    // 1. 영수증에서 금액 추출 (OCR 필요)
    Long receiptAmount = extractAmountFromReceipt(receiptImage);
    
    // 2. 최근 7일 내 같은 가게에서 같은 금액으로 결제했는가?
    List<PaymentOrder> payments = paymentRepository.findByUserIdAndPlaceIdAndAmountAndRecentDays(
        userId, 
        placeId, 
        receiptAmount, 
        7
    );
    
    if (payments.isEmpty()) {
        throw new BusinessException(ErrorCode.RECEIPT_NOT_MATCHED_WITH_PAYMENT);
    }
}
```

### Phase 2: OCR 기반 영수증 인식

```java
/**
 * 추후 추가: OCR로 영수증 내용 자동 추출
 * 외부 라이브러리: Google Vision API, Naver CLOVA OCR 등
 */
private ReceiptData extractReceiptData(MultipartFile receiptImage) {
    // 1. 이미지를 OCR 엔진에 전송
    String ocrResult = ocrService.recognize(receiptImage);
    
    // 2. 영수증에서 정보 추출
    return ReceiptData.builder()
        .shopName(extractShopName(ocrResult))
        .amount(extractAmount(ocrResult))
        .paymentTime(extractPaymentTime(ocrResult))
        .build();
}
```

### Phase 3: 머신러닝 기반 위조 탐지

```java
/**
 * 추후 추가: 머신러닝으로 위조 영수증 탐지
 * 모델: 실제 영수증 vs 위조 영수증 이미지 분류 모델
 */
private double calculateFraudProbability(MultipartFile receiptImage) {
    // 1. 이미지를 ML 모델에 입력
    double fraudScore = fraudDetectionModel.predict(receiptImage);
    
    // 2. 점수가 높으면 경고
    if (fraudScore > 0.8) {
        // 리뷰에 "신뢰도 낮음" 표시
        // 또는 관리자 검토 대상으로 지정
    }
    
    return fraudScore;
}
```

---

## 6. API 명세 (현재 MVP)

### ReviewCreateRequest

```java
public record ReviewCreateRequest(
    int rating,           // 1~5
    String content,       // 리뷰 텍스트
    List<MultipartFile> images,        // 일반 사진 (최대 5장)
    MultipartFile receiptImage         // 영수증 사진 (필수, 최대 5MB)
) {}
```

### ReviewResponse

```java
public record ReviewResponse(
    Long reviewId,
    Long userId,
    String userNickname,
    int rating,
    String content,
    String receiptImageUrl,            // ← 추가됨
    LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
            review.getId(),
            review.getUserId(),
            review.getUser().getNickname(),
            review.getRating(),
            review.getContent(),
            review.getReceiptImageUrl(),  // ← 여기!
            review.getCreatedAt()
        );
    }
}
```

### 에러 응답 예시

```json
{
  "code": "FILE_001",
  "message": "영수증 사진은 필수입니다."
}

{
  "code": "FILE_002",
  "message": "파일 크기가 최대 5MB를 초과했습니다."
}

{
  "code": "FILE_003",
  "message": "허용되지 않는 파일 형식입니다. (jpg, jpeg, png, webp만)"
}
```

---

## 7. 구현 체크리스트

### MVP 구현 시

- [ ] ErrorCode enum에 FILE_001, FILE_002, FILE_003 추가
- [ ] ReviewService.validateReceiptImage() 메서드 구현
- [ ] Review Entity에 receiptImageUrl 필드 추가
- [ ] ReviewCreateRequest에 receiptImage 필드 추가
- [ ] ReviewResponse에 receiptImageUrl 필드 추가
- [ ] S3UploadService와 연동 테스트
- [ ] 파일 크기, 포맷 검증 테스트

### 추후 확장 시

- [ ] OCR 서비스 통합
- [ ] payment_orders와 매칭 로직
- [ ] 머신러닝 모델 통합
- [ ] 리뷰 신뢰도 점수 추가

---

## 8. 참고사항

### S3 폴더 구조

```
s3://chunbaetour-bucket/
├── receipts/
│   ├── receipt_550e8400-e29b-41d4-a716-446655440000.jpg
│   ├── receipt_6ba7b810-9dad-11d1-80b4-00c04fd430c8.png
│   └── ...
├── posts/
│   └── ...
└── profiles/
    └── ...
```

### 파일명 규칙

```java
// UUID 기반으로 변환
String originalName = "영수증.jpg";
String storedName = "receipt_" + UUID.randomUUID() + ".jpg";
// 결과: receipt_550e8400-e29b-41d4-a716-446655440000.jpg
```

### 데이터베이스 마이그레이션

```sql
-- reviews 테이블에 컬럼 추가
ALTER TABLE reviews ADD COLUMN receipt_image_url VARCHAR(500);
```

---

## 요약

| 항목 | 내용 |
|------|------|
| **왜 신현민인가** | 영수증 = 결제 증빙자료 |
| **MVP 범위** | 파일 형식 검증 + S3 저장 |
| **검증 방법** | 크기(5MB), 포맷(jpg/png/webp) |
| **가짜 방지** | 사용자 신고 → 관리자 검토 |
| **추후 확장** | OCR, 결제 이력 매칭, ML 위조 탐지 |

