package com.chunbaetour.domain.auth;

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
            throw new IllegalStateException("Only USER can be promoted to MERCHANT.");
        }
        this.role = Role.MERCHANT;
    }
}
