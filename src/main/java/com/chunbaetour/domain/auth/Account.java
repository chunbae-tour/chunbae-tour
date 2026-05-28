package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(name = "companion_score", nullable = false)
    private float companionScore;

    @Column(name = "companion_review_count", nullable = false)
    private int companionReviewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Account(String email, String password, String nickname, Role role, AccountStatus status) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.status = status;
        this.language = "ko";
        this.companionScore = 0f;
        this.companionReviewCount = 0;
    }

    public static Account registerUser(String email, String hashedPassword, String nickname) {
        return Account.builder()
                .email(email)
                .password(hashedPassword)
                .nickname(nickname)
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    /**
     * <b>테스트 시드 전용 정적 팩토리</b> — 운영 코드에서 호출 금지.
     *
     * <p>회원가입 흐름({@link #registerUser})은 USER/ACTIVE만 생성한다. 통합 테스트가 MERCHANT/ADMIN
     * 또는 SUSPENDED/DELETED 상태의 시드 계정을 필요로 할 때, 이전에는 {@code src/test}의 헬퍼가
     * reflection으로 role/status를 강제 주입했다. reflection은 필드 이름 오타나 시그니처 변경에 깨지기
     * 쉬워 도메인 변경 시 위험하다.
     *
     * <p><b>가시성 강제</b>: {@code package-private}로 두어 동일 패키지({@code com.chunbaetour.domain.auth})
     * 안에서만 호출 가능. 본 패키지의 유일한 정식 호출자는 {@code src/test}의 {@code AccountSeedFactory}이며,
     * 다른 도메인 운영 코드(place, yeopjeon 등)는 컴파일 단계에서 호출 차단된다.
     *
     * @param email          이메일 (회원가입 흐름의 정규화 거치지 않음 — 호출자가 lowercase 책임)
     * @param hashedPassword 해시된 비밀번호 (BCrypt — 호출자가 PasswordHasher로 해싱)
     * @param nickname       닉네임
     * @param role           원하는 role (USER/MERCHANT/ADMIN)
     * @param status         원하는 status (ACTIVE/SUSPENDED/DELETED)
     */
    static Account createForSeed(
            String email, String hashedPassword, String nickname, Role role, AccountStatus status) {
        return Account.builder()
                .email(email)
                .password(hashedPassword)
                .nickname(nickname)
                .role(role)
                .status(status)
                .build();
    }

    /** 상인 승인 시 USER → MERCHANT 권한 상승 (STORY-09). USER 이외의 role은 승격 불가. */
    public void promoteToMerchant() {
        if (this.role != Role.USER) {
            throw new BusinessException(ErrorCode.MERCHANT_APPLICATION_STATUS_INVALID);
        }
        this.role = Role.MERCHANT;
    }

    /**
     * 계정 정지 처리 (KAN-92 신고 처리 SUSPEND 액션).
     * status = SUSPENDED. 해제는 별도 관리자 기능으로 처리.
     */
    public void suspend() {
        if (this.status == AccountStatus.DELETED) {
            throw new IllegalStateException("탈퇴 계정은 정지할 수 없습니다. accountId=" + this.id);
        }
        this.status = AccountStatus.SUSPENDED;
    }

    /**
     * 상인 인증 취소 (KAN-92 신고 처리 REVOKE_MERCHANT 액션).
     * MERCHANT → USER 권한 하향.
     */
    public void revokeToUser() {
        if (this.role != Role.MERCHANT) {
            throw new IllegalStateException("MERCHANT 계정만 USER로 권한 회수할 수 있습니다. accountId=" + this.id);
        }
        this.role = Role.USER;
    }

    /**
     * 마이페이지 PATCH /users/me에서 호출되는 partial update 도메인 메서드 (Epic A S2, KAN-127).
     *
     * <p>null 인자는 변경 미요청으로 간주해 무시 (PATCH partial update semantics).
     * 닉네임 unique/형식 검증은 호출자(Service)가 책임 — 본 메서드는 도메인 상태 갱신만.
     *
     * <p>변경 불가 필드(email/password/role/status/companionScore/companionReviewCount)는 본 메서드의
     * 시그니처에 부재 → 컴파일 단계에서 우발적 노출 차단. 비밀번호 변경/이메일 변경은 별도 도메인 흐름.
     *
     * @param nickname        새 닉네임 (null = 변경 안 함). 호출자가 unique/형식 검증 후 전달.
     * @param language        새 언어 코드 (null = 변경 안 함). 호출자가 화이트리스트 검증.
     * @param profileImageUrl 새 프로필 이미지 URL (null = 변경 안 함). 호출자가 URL 형식 검증.
     */
    public void updateProfile(String nickname, String language, String profileImageUrl) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (language != null) {
            this.language = language;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    /**
     * 회원 탈퇴 — soft delete (Epic C S1, KAN-143).
     *
     * <p>{@code deletedAt}과 {@code status=DELETED}를 같은 트랜잭션에서 동시에 세팅한다.
     * <ul>
     *   <li>{@code deletedAt}은 {@code @SQLRestriction("deleted_at IS NULL")}과 짝을 이뤄 후속 조회에서 자동 제외.</li>
     *   <li>{@code status=DELETED}는 응답 직렬화/관리자 화면에서 탈퇴 상태를 명시적으로 표현할 때 사용.</li>
     * </ul>
     * 둘 중 하나만 세팅되면 "조회는 되는데 상태는 ACTIVE", 혹은 그 반대의 불일치 상태가 만들어진다.
     * 도메인 메서드로 묶어 분기 누락을 컴파일 단계에서 막는다.
     *
     * <p><b>멱등 정책</b>: 이미 {@code DELETED} 상태인 Account에 다시 호출하면 {@link IllegalStateException}.
     * 순차 호출에서는 첫 탈퇴 직후 access blacklist 등록으로 두 번째 호출이 인증 단계에서 AUTH_013으로 차단된다.
     *
     * <p><b>알려진 제약 — 동시 요청 race</b>: 두 요청이 거의 동시에 인증 필터를 통과하면 둘 다 ACTIVE
     * 상태를 읽고 softDelete를 시도할 수 있다. 본 슬라이스에는 row lock/{@code @Version}/CAS UPDATE가
     * 없어 두 번째 호출이 본 가드에 도달해 {@link IllegalStateException}을 throw한다 (PR #207 hyeonmin02
     * 🟡 review). 첫 호출은 정상 204, 두 번째 호출은 500이 된다.
     * <ul>
     *   <li>현재 영향: 사용자 본인이 동일 토큰으로 동시 호출하는 시나리오 자체가 비정상 (UX/스크립트 결함).</li>
     *   <li>S2 (KAN-144) 정식 처리: ACCOUNT_DELETED audit event 발행 시점 중복 차단 위해 lock/version/CAS
     *       UPDATE 도입 + 멱등(204) 정책 채택 결정. 본 슬라이스는 변경 최소화 위해 도메인 가드만 유지.</li>
     * </ul>
     *
     * <p>호출자(UserMeService.deleteMe)는 결정에 따라 가드 도달 시 silent return으로 멱등 처리하거나
     * 본 예외를 그대로 전파할 수 있다.
     *
     * <p>본 메서드는 도메인 상태 전이만 담당. 토큰 무효화 cascade(Redis refresh 삭제 + access blacklist
     * 등록) + audit log는 호출자(UserMeService.deleteMe) 책임이며 트랜잭션 commit 후에 실행된다.
     *
     * @param now 탈퇴 시각. 호출자가 {@link java.time.Clock}으로 주입 — 테스트 결정성 확보. null 금지.
     * @throws IllegalArgumentException {@code now}가 null인 경우 — soft-delete 불변식
     *         ({@code deletedAt != null} ⇔ {@code status == DELETED}) 보호.
     * @throws IllegalStateException 이미 {@code DELETED} 상태인 경우.
     */
    public void softDelete(LocalDateTime now) {
        // 도메인 불변식 가드 — now가 null이면 status=DELETED인데 deletedAt=null인 partial 상태가
        // 만들어져 @SQLRestriction("deleted_at IS NULL")이 행을 살아있는 것으로 잘못 인식.
        // 호출자(UserMeService.deleteMe)는 항상 LocalDateTime.now(clock)으로 호출하지만,
        // 외부 호출자/테스트가 우회하지 못하도록 도메인 자체에서 방어. PR #207 CodeRabbit 리뷰 반영.
        if (now == null) {
            throw new IllegalArgumentException("탈퇴 시각(now)은 null일 수 없습니다.");
        }
        if (this.status == AccountStatus.DELETED) {
            throw new IllegalStateException(
                    "이미 탈퇴 처리된 계정입니다. accountId=" + this.id);
        }
        this.status = AccountStatus.DELETED;
        this.deletedAt = now;
    }
}
