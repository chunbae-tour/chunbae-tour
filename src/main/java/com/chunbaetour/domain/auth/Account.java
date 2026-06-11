package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.converter.AccountNumberEncryptConverter;
import com.chunbaetour.domain.report.entity.SanctionType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
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
// oauth 복합 UNIQUE는 @Column으로 표현 불가 → @Table에 명시(마이그레이션 uk_users_oauth_provider_id와 일치).
// phone UNIQUE는 phone 필드의 @Column(unique=true)로 선언됨.
@Table(name = "users", uniqueConstraints =
        @UniqueConstraint(name = "uk_users_oauth_provider_id", columnNames = {"oauth_provider", "oauth_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이메일은 nullable — 소셜(예: 카카오 이메일 미동의) 가입자는 이메일이 없을 수 있다(주 식별자는 oauth_id).
    // 로컬(이메일/비번) 가입은 SignupService가 항상 값을 채운다. UNIQUE 유지(MySQL UNIQUE는 NULL 다중 허용).
    @Column(unique = true, length = 255)
    private String email;

    // 소셜(카카오/네이버) 가입자는 비밀번호가 없어 nullable. 로컬(이메일/비번) 계정은 항상 값 보유.
    @Column(length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    // ===== 소셜 로그인 + 추가 프로필 (KAN 소셜로그인) =====
    // 소셜 가입 흐름에서만 채워진다(로컬 계정은 null).
    @Column(length = 50)
    private String name;

    // 전화번호 원문 — AES-GCM 암호화 저장(표시용). 랜덤 IV라 UNIQUE/동등비교 불가 → 중복판별은 phoneHash로.
    @Convert(converter = AccountNumberEncryptConverter.class)
    @Column(length = 255)
    private String phone;

    // 전화번호 HMAC-SHA256 해시 — 중복가입 판별 UNIQUE 키(결정적). 원문 암호화와 분리.
    @Column(name = "phone_hash", length = 64, unique = true)
    private String phoneHash;

    private LocalDate birthdate;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", length = 20)
    private OauthProvider oauthProvider;

    @Column(name = "oauth_id", length = 100)
    private String oauthId;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(name = "companion_score", nullable = false)
    private double companionScore;

    @Column(name = "companion_review_count", nullable = false)
    private int companionReviewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "suspended_reason", length = 500)
    private String suspendedReason;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    /** 시스템 자동 제재 단계 — USER/MERCHANT 도메인 신고 또는 크로스도메인 트리거. null = 제재 없음. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sanction_type", length = 20)
    private SanctionType sanctionType;

    /** 시스템 제재 종료 예정 시각. PERMANENT이면 null. */
    @Column(name = "sanction_end_at")
    private LocalDateTime sanctionEndAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Account(String email, String password, String nickname, Role role, AccountStatus status,
                    String name, String phone, String phoneHash, LocalDate birthdate,
                    OauthProvider oauthProvider, String oauthId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.status = status;
        this.name = name;
        this.phone = phone;
        this.phoneHash = phoneHash;
        this.birthdate = birthdate;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
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
     * 소셜(카카오/네이버) 신규 가입 — 비밀번호 없이 oauth 식별자 + 추가 프로필로 USER/ACTIVE 계정 생성.
     *
     * <p>중복 검증(이메일/닉네임/전화/oauth)은 호출자({@code OauthSignupService})가 책임진다.
     *
     * @param provider  소셜 공급자
     * @param oauthId   공급자 사용자 식별자
     * @param email     이메일 (호출자가 lowercase 정규화)
     * @param nickname  닉네임
     * @param name      실명
     * @param phone     전화번호 원문 (호출자가 숫자만 정규화 — 저장 시 AES 암호화됨)
     * @param phoneHash 전화번호 HMAC 해시 (호출자가 PhoneHasher로 산출 — 중복판별 UNIQUE 키)
     * @param birthdate 생년월일
     */
    public static Account registerOauthUser(
            OauthProvider provider, String oauthId, String email, String nickname,
            String name, String phone, String phoneHash, LocalDate birthdate) {
        return Account.builder()
                .email(email)
                .nickname(nickname)
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .oauthProvider(provider)
                .oauthId(oauthId)
                .name(name)
                .phone(phone)
                .phoneHash(phoneHash)
                .birthdate(birthdate)
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

    /**
     * 상인 승인 시 권한 상승 (STORY-09).
     * USER → MERCHANT, MERCHANT는 멱등(이미 상인이면 skip), ADMIN은 승격 불가.
     * 1상인 다중 가게 지원: MERCHANT가 추가 가게 신청 승인을 받을 때 재호출되므로 멱등 필수.
     */
    public void promoteToMerchant() {
        if (this.role == Role.MERCHANT) {
            return;
        }
        if (this.role != Role.USER) {
            throw new BusinessException(ErrorCode.MERCHANT_APPLICATION_STATUS_INVALID);
        }
        this.role = Role.MERCHANT;
    }

    /**
     * 계정 정지 처리 (KAN-92 신고 처리 SUSPEND 액션).
     * status = SUSPENDED. 해제는 별도 관리자 기능으로 처리.
     *
     * <p>신고 자동 정지 경로 전용 — 사유/기간 미입력의 무기한 정지. 운영자 수동 정지는
     * {@link #suspend(String, LocalDateTime)}를 사용한다.
     */
    public void suspend() {
        if (this.status == AccountStatus.DELETED) {
            throw new IllegalStateException("탈퇴 계정은 정지할 수 없습니다. accountId=" + this.id);
        }
        this.status = AccountStatus.SUSPENDED;
    }

    /**
     * 운영자 수동 정지 — 사유/만료시각 세팅 (KAN-180 Admin S02).
     *
     * <p><b>재정지 갱신 허용</b>: 이미 {@code SUSPENDED}인 계정에 재호출하면 사유/기간을 덮어쓴다
     * (예외 없음). 운영자가 정지 사유를 정정하거나 기간을 연장하는 정상 시나리오.
     *
     * <p><b>상태 무결성 가드</b>: {@code DELETED} 계정은 정지 불가 — soft-delete된 계정을 다시
     * 활성 상태군으로 끌어올리는 모순을 막는다.
     *
     * @param reason         정지 사유 (운영자 입력)
     * @param suspendedUntil 정지 만료 시각 (null = 무기한). 호출자가 기간으로부터 산출.
     * @throws BusinessException 이미 {@code DELETED} 상태인 경우 ({@link ErrorCode#USER_ALREADY_DELETED}, 400).
     */
    public void suspend(String reason, LocalDateTime suspendedUntil) {
        if (this.status == AccountStatus.DELETED) {
            throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
        }
        this.status = AccountStatus.SUSPENDED;
        this.suspendedReason = reason;
        this.suspendedUntil = suspendedUntil;
    }

    /**
     * 운영자 정지 해제 — status = ACTIVE + 정지 사유/만료시각 초기화 (KAN-180 Admin S02).
     *
     * <p>{@code DELETED} 계정에는 적용하지 않는다 — 탈퇴 계정을 활성 상태로 되살리는 모순 방지.
     *
     * @throws BusinessException {@code DELETED} 상태인 경우 ({@link ErrorCode#USER_ALREADY_DELETED}, 400).
     */
    public void unsuspend() {
        if (this.status == AccountStatus.DELETED) {
            throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
        }
        this.status = AccountStatus.ACTIVE;
        this.suspendedReason = null;
        this.suspendedUntil = null;
        this.sanctionType = null;
        this.sanctionEndAt = null;
    }

    /**
     * 시스템 자동 제재 적용 — 도메인별 신고 누적 또는 크로스도메인 트리거.
     * WARNING과 NONE은 계정 정지를 유발하지 않으므로 무시.
     * 기존 제재가 신규보다 높거나 같으면 덮어쓰지 않음 (다운그레이드 방지).
     */
    public void applySystemSanction(SanctionType type, LocalDateTime sanctionEndAt) {
        if (this.status == AccountStatus.DELETED) return;
        if (type == SanctionType.WARNING || type == SanctionType.NONE) return;
        if (type == SanctionType.PERMANENT && sanctionEndAt != null) {
            throw new IllegalArgumentException("PERMANENT 제재는 sanctionEndAt이 null이어야 합니다.");
        }
        if (type != SanctionType.PERMANENT && sanctionEndAt == null) {
            throw new IllegalArgumentException("PERMANENT 외 제재는 sanctionEndAt이 필수입니다.");
        }
        // 관리자 수동 정지(sanctionType=null+SUSPENDED)는 시스템 제재로 덮어쓰지 않음
        if (this.status == AccountStatus.SUSPENDED && this.sanctionType == null) return;
        if (this.sanctionType != null) {
            if (type.severity() < this.sanctionType.severity()) return;
            if (type == this.sanctionType) {
                if (sanctionEndAt != null
                        && (this.sanctionEndAt == null || sanctionEndAt.isAfter(this.sanctionEndAt))) {
                    this.sanctionEndAt = sanctionEndAt;
                }
                return;
            }
        }
        this.status = AccountStatus.SUSPENDED;
        this.sanctionType = type;
        this.sanctionEndAt = sanctionEndAt;
    }

    /**
     * 시스템 제재 해제 — 스케줄러 자동 만료 또는 로그인 시점 만료 체크.
     * PERMANENT는 자동 해제 불가 — 관리자 수동 처리 필요.
     */
    public void clearSystemSanction() {
        if (this.status == AccountStatus.DELETED) return;
        if (this.sanctionType == SanctionType.PERMANENT) {
            throw new IllegalStateException("PERMANENT 제재는 자동 해제 불가. 관리자 수동 해제 필요. accountId=" + this.id);
        }
        this.status = AccountStatus.ACTIVE;
        this.sanctionType = null;
        this.sanctionEndAt = null;
    }

    /** 관리자 강제 시스템 제재 해제 — PERMANENT 포함 모든 단계 해제 가능. */
    public void adminClearSystemSanction() {
        if (this.status == AccountStatus.DELETED) return;
        if (this.sanctionType == null) return;
        this.status = AccountStatus.ACTIVE;
        this.sanctionType = null;
        this.sanctionEndAt = null;
    }

    /** 시스템 제재 기간 만료 여부 — sanctionEndAt 이 null(PERMANENT 또는 미제재)이면 false. */
    public boolean isSystemSanctionExpired(LocalDateTime now) {
        return this.sanctionType != null
                && this.sanctionEndAt != null
                && !this.sanctionEndAt.isAfter(now);
    }

    /** 시스템 제재가 현재 활성 상태인지 — PERMANENT(null)이거나 sanctionEndAt 이 now 이후면 true. */
    public boolean isCurrentlySanctioned(LocalDateTime now) {
        return this.sanctionType != null
                && (this.sanctionEndAt == null || this.sanctionEndAt.isAfter(now));
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
    // 동행 리뷰 등록 시 증분식 평균 갱신 — (currentScore * count + newScore) / (count + 1)
    public void addCompanionReview(int score) {
        this.companionScore = (this.companionScore * this.companionReviewCount + score) / (double) (this.companionReviewCount + 1);
        this.companionReviewCount++;
    }

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
