package com.chunbaetour.domain.support;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.PasswordHasher;
import com.chunbaetour.domain.auth.Role;
import java.lang.reflect.Field;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 통합 테스트 전용 계정 시드 헬퍼.
 *
 * <p>운영 코드의 회원가입 흐름은 {@link Role#USER}만 생성한다. S5 통합 테스트는
 * MERCHANT/ADMIN 계정도 필요하므로 본 헬퍼가 reflect로 role/status를 강제 주입한다.
 *
 * <p>운영 코드에 테스트 전용 factory 메서드를 추가하지 않기 위해 본 클래스는 {@code src/test}에 위치한다.
 * 따라서 운영 빌드에 포함되지 않고, {@link Account}의 불변 도메인 원칙(setter 없음)도 유지된다.
 *
 * <p>주의: reflect 사용은 테스트 한정 우회 수단이며 운영 코드에서 따라하면 안 된다. 본 클래스를 통과하지
 * 않는 새로운 시드 경로가 생기지 않도록 모든 통합 테스트는 본 헬퍼를 사용한다.
 */
@Component
@RequiredArgsConstructor
public class AccountSeedFactory {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;

    /**
     * 지정된 role/status로 계정을 즉시 영속화한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link Account#registerUser}로 USER/ACTIVE 기본 계정 생성</li>
     *   <li>비밀번호는 정상 BCrypt 해시 (로그인 검증을 실제 흐름과 동일하게 통과시키기 위해)</li>
     *   <li>reflect로 role/status를 인자값으로 덮어쓰기</li>
     *   <li>Repository.save로 영속화 → 통합 테스트가 실제 DB에서 조회 가능</li>
     * </ol>
     *
     * @param email     로그인 ID (이메일). 테스트 간 충돌 방지를 위해 호출자가 unique 보장
     * @param password  평문 비밀번호 — BCrypt 해시 후 저장
     * @param nickname  닉네임 — 테스트 간 unique 보장 책임은 호출자
     * @param role      USER/MERCHANT/ADMIN 중 원하는 role
     * @param status    ACTIVE/SUSPENDED/DELETED 중 원하는 상태
     * @return 영속화된 Account (id 채워진 상태)
     */
    public Account seed(String email, String password, String nickname, Role role, AccountStatus status) {
        Account account = Account.registerUser(email, passwordHasher.hash(password), nickname);
        writeField(account, "role", role);
        writeField(account, "status", status);
        return accountRepository.save(account);
    }

    /**
     * MERCHANT/ACTIVE 시드 단축 메서드.
     */
    public Account seedMerchant(String email, String password, String nickname) {
        return seed(email, password, nickname, Role.MERCHANT, AccountStatus.ACTIVE);
    }

    /**
     * ADMIN/ACTIVE 시드 단축 메서드.
     */
    public Account seedAdmin(String email, String password, String nickname) {
        return seed(email, password, nickname, Role.ADMIN, AccountStatus.ACTIVE);
    }

    private static void writeField(Account account, String name, Object value) {
        try {
            Field field = Account.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(account, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("테스트 시드용 reflect 주입 실패: " + name, e);
        }
    }
}
