package com.chunbaetour.domain.auth;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /** 소셜 신원으로 계정 조회 — (oauth_provider, oauth_id)는 UNIQUE. */
    Optional<Account> findByOauthProviderAndOauthId(OauthProvider oauthProvider, String oauthId);

    /**
     * 탈퇴(soft-delete) row 포함 전화번호 해시 존재 검사 — DB UNIQUE(phone_hash)와 검사 범위를 일치시킨다.
     *
     * <p>전화번호 원문(phone)은 AES 암호화 저장이라 동등비교 불가 → 중복판별은 결정적 해시(phone_hash)로 한다.
     * 파생 쿼리(existsBy…)는 {@code @SQLRestriction("deleted_at IS NULL")}으로 탈퇴 계정을 못 봐서,
     * "탈퇴자가 점유한 번호"로 가입 시 INSERT가 DB UNIQUE 위반(500)으로 떨어진다. 본 native 쿼리로 탈퇴
     * row까지 봐 {@code DUPLICATE_PHONE}로 정확히 변환한다(email/nickname/oauth와 동일 패턴).
     *
     * <p><b>테이블명 {@code users} 하드코딩</b>: native 쿼리라 {@code @Table(name="users")} 변경 시 함께 갱신.
     */
    @Query(value = "SELECT COUNT(*) FROM users WHERE phone_hash = :phoneHash", nativeQuery = true)
    long countByPhoneHashIncludingDeleted(@Param("phoneHash") String phoneHash);

    /**
     * 탈퇴 row 포함 소셜 신원 존재 검사 — DB UNIQUE(oauth_provider, oauth_id)와 범위 일치.
     *
     * <p>{@link #findByOauthProviderAndOauthId}는 탈퇴 계정을 못 봐서, 탈퇴자가 점유한 소셜 신원으로
     * 재가입 시 INSERT가 DB UNIQUE 위반(500)으로 떨어진다. 본 쿼리로 {@code OAUTH_ALREADY_REGISTERED}로 변환.
     */
    @Query(value = "SELECT COUNT(*) FROM users WHERE oauth_provider = :provider AND oauth_id = :oauthId",
            nativeQuery = true)
    long countByOauthIdentityIncludingDeleted(@Param("provider") String provider, @Param("oauthId") String oauthId);

    /**
     * 탈퇴 row 포함 닉네임 존재 검사 — DB UNIQUE(nickname)와 범위 일치.
     *
     * <p>{@link #existsByNickname}은 {@code @SQLRestriction}으로 탈퇴 계정을 못 봐서, 탈퇴자가 점유한 닉네임으로
     * 가입 시 INSERT가 DB UNIQUE 위반(500)으로 떨어진다(email/phone과 동일 부류). 가입 race recheck에서 본
     * 쿼리로 탈퇴 row까지 봐 {@code DUPLICATE_NICKNAME}로 정확히 변환한다.
     */
    @Query(value = "SELECT COUNT(*) FROM users WHERE nickname = :nickname", nativeQuery = true)
    long countByNicknameIncludingDeleted(@Param("nickname") String nickname);

    /**
     * 본인 외 닉네임 중복 체크 — PATCH /users/me (KAN-127 S2)에서 사용.
     *
     * <p>{@link #existsByNickname}을 그대로 쓰면 자기 자신 닉네임에도 true가 나와 변경 거부됨.
     * 본인 id를 제외해 "다른 사용자가 같은 닉네임 점유 중인지"만 검사.
     */
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    Optional<Account> findByEmail(String email);

    /** 상인 승인 시 role 변경을 위한 비관적 락 조회 (STORY-09). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    /**
     * 탈퇴 row까지 포함해 동일 email이 존재하는지 검사 (Epic C S2, KAN-144).
     *
     * <p><b>왜 별도 메서드인가</b>: {@link #existsByEmail}는 {@code @SQLRestriction("deleted_at IS NULL")}으로
     * soft-deleted row를 필터하므로 "탈퇴자가 점유한 email"을 감지하지 못한다. 회원가입 흐름의 race recheck
     * 분기({@code SignupService.signup}의 {@link org.springframework.dao.DataIntegrityViolationException} catch)는
     * "DB가 unique violation을 던졌는데 existsByEmail이 false" 시 fallthrough해 500이 발생하던 잠재 버그가 있었음
     * (Epic C S1까지 미발현 — S2 통합 테스트에서 노출). 본 메서드는 native SQL로 @SQLRestriction을 우회해
     * soft-deleted row까지 보고, SignupService가 정확한 {@code AUTH_008}로 변환할 수 있게 한다.
     *
     * <p>ADR ({@code docs/operations/account-withdrawal-policy.md}) §3 — 동일 email 재가입 차단(c안)의 회귀 가드.
     *
     * <p><b>collation-safe 비교</b>: 호출자({@code SignupService.signup})가 이미 {@code toLowerCase(Locale.ROOT)}로
     * 정규화해 전달하지만, 본 쿼리도 {@code LOWER(email) = LOWER(:email)}로 양방향 정규화한다. DB column collation이
     * {@code utf8mb4_bin}(case-sensitive) 같은 설정으로 변경돼도 "User@example.com"으로 탈퇴한 사람이
     * "user@example.com"으로 재가입하는 우회 케이스를 차단. (PR #217 lim-haeun review)
     * 성능: 본 메서드는 회원가입 race recheck 분기 한 곳에서만 호출되어 호출 빈도 낮음 — LOWER 함수로 인한
     * 인덱스 미적용 오버헤드는 운영상 무시 가능.
     *
     * <p><b>테이블명 {@code users} 하드코딩</b>: native query라 {@code Account} 엔티티의 {@code @Table(name="users")}
     * 변경 시 런타임 오류 발생. {@code @SQLRestriction} 우회 목적으로 JPQL 파생 쿼리가 불가능해 native 사용은 불가피.
     * Account 테이블명 변경 시 본 쿼리도 함께 갱신 — ADR §6 운영 체크리스트의 회귀 가드 항목으로 추적.
     *
     * @param email 검사할 이메일 (호출자에서 lowercase 정규화 + 본 쿼리에서 LOWER 재정규화로 이중 안전)
     * @return soft-deleted를 포함해 동일 email row 수 (1 이상이면 존재). MySQL은 EXISTS(...)를 BIGINT 1/0으로
     *         리턴하므로 Java boolean 자동 매핑이 깨지는 케이스가 있어 {@code long}으로 받아 호출자가 비교.
     */
    @Query(value = "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(:email)", nativeQuery = true)
    long countByEmailIncludingDeleted(@Param("email") String email);

    /**
     * 회원 탈퇴 atomic CAS UPDATE (Epic C S2, KAN-144).
     *
     * <p><b>왜 CAS UPDATE인가</b>: 회원 탈퇴는 두 가지 동시성 사고를 모두 차단해야 한다.
     * <ul>
     *   <li>(a) {@code ACCOUNT_DELETED} audit event가 동일 사용자에 대해 두 번 발행 — SIEM 룰 노이즈/잘못된
     *       탈취 의심 알람 트리거</li>
     *   <li>(b) UserLike cascade 등 사이드이펙트가 2번 실행 — DB는 멱등이지만 SecurityAuditLogger처럼 외부
     *       시스템에 신호를 보내는 흐름은 한 번이어야 함</li>
     * </ul>
     * Read-then-write 패턴({@code findByIdWithLock} + {@link Account#softDelete})은 PESSIMISTIC_WRITE 락만으로
     * 두 동시 요청을 직렬화하지만, {@code @SQLRestriction("deleted_at IS NULL")}이 FOR UPDATE 쿼리와 결합할 때
     * 일부 Hibernate/MySQL 조합에서 tx2가 락 해제 후 stale snapshot을 보는 케이스가 관측됨. CAS UPDATE는
     * DB의 단일 명령으로 "조건 만족 시에만 UPDATE" 의미라 race-window 자체가 없다 — 영향받은 row 수 0이면
     * "이미 누군가 탈퇴 처리했음" 신호.
     *
     * <p><b>WHERE 조건</b>: {@code deleted_at IS NULL} — 도메인 invariant({@code deletedAt IS NULL ⇔ ACTIVE/SUSPENDED})
     * 와 동일. 추가로 {@code status <> 'DELETED'}를 두지 않은 이유는 두 컬럼이 항상 짝으로 움직이는 invariant라
     * 한쪽만 검사해도 충분 (방어적으로 status 변경 직전 가드도 {@link Account#softDelete}에 존재).
     *
     * <p><b>도메인 메서드와 관계</b>: {@link Account#softDelete}는 비-동시성 컨텍스트 + 도메인 단위 테스트를
     * 위해 유지. 운영 흐름({@code UserMeService.deleteMe})은 본 CAS UPDATE를 사용해 race-safe하게 처리.
     *
     * <p><b>{@code updatedAt} 수동 세팅</b>: JPQL bulk update는 JPA Auditing {@code EntityListener}를 거치지
     * 않아 {@code @LastModifiedDate} 컬럼이 자동 갱신되지 않는다. "최근 변경 계정" 기준 조회/배치가 탈퇴 시점을
     * 누락하지 않도록 본 쿼리에서 {@code updated_at}을 명시적으로 동일 시각으로 세팅. (PR #217 hyeonmin02 review)
     *
     * <p><b>enum 파라미터 바인딩</b>: 기존 JPQL에 {@code AccountStatus.DELETED} FQCN을 박았으나 패키지 이동 시
     * 런타임 오류 위험이 있어 파라미터로 분리. 호출자({@code UserMeService.deleteMe})가
     * {@link AccountStatus#DELETED} 상수를 전달. (PR #217 lim-haeun review)
     *
     * @param userId        탈퇴 대상 사용자 ID
     * @param now           탈퇴 시각 (호출자가 {@link java.time.Clock}으로 주입)
     * @param deletedStatus 항상 {@link AccountStatus#DELETED} — enum FQCN을 JPQL에 박지 않기 위한 분리
     * @return UPDATE된 row 수. {@code 1} = 탈퇴 성공 (호출자 책임), {@code 0} = 이미 탈퇴됨(또는 존재하지 않음)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Account a SET a.status = :deletedStatus, "
            + "a.deletedAt = :now, a.updatedAt = :now "
            + "WHERE a.id = :userId AND a.deletedAt IS NULL")
    int markAsDeleted(@Param("userId") Long userId,
                       @Param("now") LocalDateTime now,
                       @Param("deletedStatus") AccountStatus deletedStatus);

    /**
     * 운영자 사용자 목록 검색 — keyword(닉네임/이메일 부분일치) + status + role 필터 + cursor 페이징 (KAN-180 Admin S02).
     *
     * <p>모든 필터는 {@code null}이면 미적용(전체). cursor는 id 내림차순 keyset 페이징 —
     * {@code cursorId}보다 작은 id만 조회해 다음 페이지를 sentinel(size+1) 방식으로 판단(서비스 책임).
     *
     * <p>{@code @SQLRestriction("deleted_at IS NULL")}이 자동 적용되어 탈퇴(DELETED) 계정은 결과에서 제외된다 —
     * 운영자 사용자 관리는 활성/정지 계정 대상. keyword는 {@code CONCAT('%',:keyword,'%')} LIKE이며 공백 문자열은
     * 호출자(서비스)가 null로 정규화해 전달.
     */
    @Query("SELECT a FROM Account a WHERE "
            + "(:keyword IS NULL OR a.nickname LIKE CONCAT('%', :keyword, '%') "
            + "OR a.email LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:status IS NULL OR a.status = :status) "
            + "AND (:role IS NULL OR a.role = :role) "
            + "AND (:cursorId IS NULL OR a.id < :cursorId) "
            + "ORDER BY a.id DESC")
    List<Account> searchForAdmin(@Param("keyword") String keyword,
                                 @Param("status") AccountStatus status,
                                 @Param("role") Role role,
                                 @Param("cursorId") Long cursorId,
                                 Pageable pageable);

    /** 정지 상태 계정 수 — S03 대시보드 의존 (KAN-181). */
    long countByStatus(AccountStatus status);

    /** 특정 시각 이후 가입 계정 수 — 오늘 신규 가입 카운트용. S03 대시보드 의존 (KAN-181). */
    long countByCreatedAtGreaterThanEqual(LocalDateTime start);

    /** 시스템 제재 만료 계정 목록 — SuspendExpiryScheduler 일괄 해제용.
     * status=SUSPENDED 필터: 이미 해제된(ACTIVE) 계정의 stale 필드 재처리 방지.
     * PERMANENT(sanction_end_at IS NULL)는 조건 불충족으로 자동 제외. */
    @Query("SELECT a FROM Account a WHERE a.status = com.chunbaetour.domain.auth.AccountStatus.SUSPENDED AND a.sanctionEndAt < :now")
    List<Account> findExpiredSystemSanctions(@Param("now") LocalDateTime now);
}
