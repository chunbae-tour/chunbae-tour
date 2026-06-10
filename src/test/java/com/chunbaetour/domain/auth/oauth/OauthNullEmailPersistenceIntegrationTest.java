package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 이메일 없는 소셜 계정의 영속성 검증 (길 A, hyeonmin02 리뷰) — email=null 계정이 실제 DB에 insert되고,
 * UNIQUE(email)이 NULL 다중을 허용해 이메일 없는 소셜 계정이 여러 개여도 충돌하지 않는지 확인한다.
 * (마이그레이션↔엔티티 스키마 정합은 FlywayEntitySchemaValidationIntegrationTest가 별도 검증.)
 */
@SpringBootTest
class OauthNullEmailPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    // 공유 컨테이너(Singleton Containers) 규약 — 본 클래스가 만든 계정은 본 클래스가 정리.
    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
    }

    @DisplayName("email=null 소셜 계정 insert 성공 + 재조회 시 email null 유지")
    @Test
    void persistsOauthAccountWithoutEmail() {
        Account account = Account.registerOauthUser(
                OauthProvider.KAKAO, "null-email-oid-1", null, "널이멜일",
                "홍길동", "01055550001", "null-email-hash-1", LocalDate.of(1990, 1, 1));

        Account saved = accountRepository.saveAndFlush(account);
        Account found = accountRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getEmail()).isNull();
        assertThat(found.getOauthProvider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(found.getOauthId()).isEqualTo("null-email-oid-1");
    }

    @DisplayName("email=null 소셜 계정 2개 insert — UNIQUE(email)은 NULL 다중 허용이라 충돌하지 않는다")
    @Test
    void allowsMultipleAccountsWithNullEmail() {
        Account first = Account.registerOauthUser(
                OauthProvider.KAKAO, "null-email-oid-2", null, "널이멜이",
                "김첫째", "01055550002", "null-email-hash-2", LocalDate.of(1991, 2, 2));
        Account second = Account.registerOauthUser(
                OauthProvider.NAVER, "null-email-oid-3", null, "널이멜삼",
                "이둘째", "01055550003", "null-email-hash-3", LocalDate.of(1992, 3, 3));

        accountRepository.saveAndFlush(first);
        accountRepository.saveAndFlush(second);   // email UNIQUE 충돌 시 여기서 예외 → 테스트 실패

        assertThat(accountRepository.findById(first.getId()).orElseThrow().getEmail()).isNull();
        assertThat(accountRepository.findById(second.getId()).orElseThrow().getEmail()).isNull();
    }
}
