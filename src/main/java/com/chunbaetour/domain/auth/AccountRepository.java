package com.chunbaetour.domain.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

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
}
