package com.chunbaetour.domain.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 통합 테스트 전용 계정 시드 헬퍼.
 *
 * <p>운영 코드의 회원가입 흐름은 {@link Role#USER}만 생성한다. S5 통합 테스트는 MERCHANT/ADMIN 계정도
 * 필요하므로 본 헬퍼가 {@link Account#createForSeed} 정적 팩토리로 role/status를 명시 지정해 계정을
 * 생성한다.
 *
 * <p><b>패키지 위치 결정 (중요)</b>:
 * <ul>
 *   <li>{@link Account#createForSeed}는 {@code package-private}이라 동일 패키지
 *       ({@code com.chunbaetour.domain.auth})에서만 호출 가능하다.</li>
 *   <li>본 헬퍼를 {@code src/test/java/com/chunbaetour/domain/auth/}에 두어 컴파일 단계에서 호출 권한을 강제.</li>
 *   <li>다른 도메인(place, yeopjeon 등)의 운영 코드가 {@code createForSeed}를 호출하려 하면 컴파일 에러.</li>
 * </ul>
 *
 * <p>이전 구현은 {@code Field.setAccessible(true)} 기반 reflection으로 주입했지만, 필드 이름 오타나
 * 시그니처 변경 시 컴파일 단계에서 못 잡고 런타임에 깨지는 위험이 있었다. 정적 팩토리는 컴파일러가
 * 인자/타입을 검증하므로 도메인 변경에 강건하다.
 *
 * <p>본 클래스는 {@code src/test}에 위치 → 운영 빌드에 포함되지 않음. 모든 통합 테스트는 본 헬퍼를
 * 거쳐 새로운 시드 경로가 새로 생기지 않도록 한다.
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
     *   <li>{@link Account#createForSeed}로 인자 그대로의 계정 생성 (필드 명시 매칭, reflection 없음)</li>
     *   <li>비밀번호는 정상 BCrypt 해시 (로그인 검증을 실제 흐름과 동일하게 통과시키기 위해)</li>
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
        Account account = Account.createForSeed(email, passwordHasher.hash(password), nickname, role, status);
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
}
