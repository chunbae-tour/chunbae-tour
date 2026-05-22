package com.chunbaetour.domain.common.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link SecretValidator} 단위 테스트.
 *
 * <p>검증 범위:
 * <ul>
 *   <li>프로파일 활성화 — prod에서만 동작, 그 외는 검증 skip</li>
 *   <li>CORS_ALLOWED_ORIGINS — 비어있음, 와일드카드, URL 형식 위반</li>
 *   <li>DB_PASSWORD — 비어있음, 짧은 길이, 약한 비밀번호, placeholder</li>
 *   <li>외부 API 키 (PORTONE_*, KAKAO_MAP_API_KEY) — 비어있음, placeholder</li>
 *   <li>오류 누적 — 여러 위반 사항을 하나의 예외로 모아 throw</li>
 * </ul>
 *
 * <p>통합 테스트 별도 미작성 사유: {@code AbstractIntegrationTest}가 local 프로파일 + .env.example
 * 더미 값으로 동작하며, validator는 prod에서만 활성화되므로 자연히 "local에서는 검증 skip" 회귀 방지를
 * 통합 테스트들이 간접적으로 보장한다. prod 부팅 실패는 본 단위 테스트의 모든 위반 케이스가 동일한
 * Environment-event-time 검증 로직(={@link SecretValidator#validate}) 경로를 사용한다.
 */
class SecretValidatorTest {

    private final SecretValidator validator = new SecretValidator();

    /** prod에서 통과해야 할 fully-populated valid Environment seed. */
    private MockEnvironment validProdEnv() {
        return prodEnv()
                .withProperty("CORS_ALLOWED_ORIGINS", "https://chunbae.tour,https://api.chunbae.tour")
                .withProperty("DB_PASSWORD", "ProdPass2026!@#")
                .withProperty("DB_USERNAME", "chunbae_prod")
                .withProperty("KAKAO_MAP_API_KEY", "DUMMY_KAKAO_VALUE_000000000000000000")
                .withProperty("PORTONE_SECRET", "DUMMY_PORTONE_SECRET_000000")
                .withProperty("PORTONE_STORE_ID", "DUMMY_PORTONE_STORE_000000")
                .withProperty("PORTONE_WEBHOOK_SECRET", "DUMMY_PORTONE_WEBHOOK_000000")
                .withProperty("PORTONE_CHANNEL_CARD", "DUMMY_PORTONE_CHANNEL_CARD_000000")
                .withProperty("PORTONE_CHANNEL_KAKAO_PAY", "DUMMY_PORTONE_CHANNEL_KAKAO_000000")
                .withProperty("PORTONE_CHANNEL_TOSS_PAY", "DUMMY_PORTONE_CHANNEL_TOSS_000000")
                .withProperty("PORTONE_CHANNEL_FOREIGN_CARD", "DUMMY_PORTONE_CHANNEL_FOREIGN_000000");
    }

    private MockEnvironment prodEnv() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }

    @Nested
    class 프로파일_활성화 {

        @Test
        void local_프로파일에서는_검증_skip() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("local");
            // 의도적으로 모든 시크릿 누락 — local이면 검증 자체가 skip되어야 함
            assertThatCode(() -> validator.validate(env)).doesNotThrowAnyException();
        }

        @Test
        void test_프로파일에서도_검증_skip() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("test");
            assertThatCode(() -> validator.validate(env)).doesNotThrowAnyException();
        }

        @Test
        void 프로파일_미지정시_검증_skip() {
            MockEnvironment env = new MockEnvironment();
            // setActiveProfiles 호출 안 함 → getActiveProfiles 빈 배열
            assertThatCode(() -> validator.validate(env)).doesNotThrowAnyException();
        }

        @Test
        void prod_프로파일_정상_시크릿_통과() {
            assertThatCode(() -> validator.validate(validProdEnv())).doesNotThrowAnyException();
        }

        @Test
        void prod_포함_다중_프로파일에서도_검증_실행() {
            MockEnvironment env = validProdEnv();
            env.setActiveProfiles("prod", "feature-x");
            assertThatCode(() -> validator.validate(env)).doesNotThrowAnyException();
        }
    }

    @Nested
    class CORS_ALLOWED_ORIGINS {

        @Test
        void 누락시_부팅_실패() {
            MockEnvironment env = validProdEnv();
            env.getPropertySources().forEach(ps -> { /* keep others */ });
            env.withProperty("CORS_ALLOWED_ORIGINS", "");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CORS_ALLOWED_ORIGINS")
                    .hasMessageContaining("비어있음");
        }

        @Test
        void 와일드카드_포함시_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty(
                    "CORS_ALLOWED_ORIGINS", "https://chunbae.tour,*");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("와일드카드");
        }

        @Test
        void 스킴_없는_URL은_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty(
                    "CORS_ALLOWED_ORIGINS", "chunbae.tour");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("스킴+호스트");
        }

        @Test
        void path_포함_origin은_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty(
                    "CORS_ALLOWED_ORIGINS", "https://chunbae.tour/api");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        }

        @Test
        void ftp_같은_허용_안되는_스킴은_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty(
                    "CORS_ALLOWED_ORIGINS", "ftp://chunbae.tour");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("스킴+호스트");
        }

        @Test
        void 콤마_구분_여러_origin_모두_유효시_통과() {
            MockEnvironment env = validProdEnv().withProperty(
                    "CORS_ALLOWED_ORIGINS",
                    "https://chunbae.tour,https://api.chunbae.tour,http://staging.chunbae.tour:8080");
            assertThatCode(() -> validator.validate(env)).doesNotThrowAnyException();
        }
    }

    @Nested
    class DB_PASSWORD {

        @Test
        void 누락시_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty("DB_PASSWORD", "");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_PASSWORD")
                    .hasMessageContaining("비어있음");
        }

        @Test
        void 짧은_비밀번호_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty("DB_PASSWORD", "Short1!");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_PASSWORD")
                    .hasMessageContaining("최소 12자");
        }

        @Test
        void 약한_비밀번호_1234_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty("DB_PASSWORD", "1234");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_PASSWORD");
        }

        @Test
        void 약한_비밀번호_password_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty("DB_PASSWORD", "password");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_PASSWORD");
        }

        @Test
        void placeholder_포함_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty("DB_PASSWORD", "your-strong-password-here");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_PASSWORD")
                    .hasMessageContaining("placeholder");
        }
    }

    @Nested
    class 외부_API_키 {

        @Test
        void KAKAO_MAP_API_KEY_누락시_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty("KAKAO_MAP_API_KEY", "");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("KAKAO_MAP_API_KEY")
                    .hasMessageContaining("비어있음");
        }

        @Test
        void KAKAO_MAP_API_KEY_더미값_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty(
                    "KAKAO_MAP_API_KEY", "your-kakao-rest-api-key-here");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("KAKAO_MAP_API_KEY")
                    .hasMessageContaining("placeholder");
        }

        @Test
        void PORTONE_SECRET_더미값_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty(
                    "PORTONE_SECRET", "your-portone-v2-api-secret");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PORTONE_SECRET");
        }

        @Test
        void PORTONE_STORE_ID_xxxxxxxx_패턴_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty(
                    "PORTONE_STORE_ID", "store-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PORTONE_STORE_ID");
        }

        @Test
        void PORTONE_채널키_4종_모두_검증_대상() {
            // 카드 채널만 placeholder로 두고 나머지는 실값
            MockEnvironment env = validProdEnv().withProperty(
                    "PORTONE_CHANNEL_CARD", "channel-key-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PORTONE_CHANNEL_CARD");
        }

        @Test
        void PORTONE_WEBHOOK_SECRET_더미값_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty(
                    "PORTONE_WEBHOOK_SECRET", "whsec_your-portone-webhook-secret");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PORTONE_WEBHOOK_SECRET");
        }
    }

    @Nested
    class 오류_누적 {

        @Test
        void 다중_위반_하나의_예외로_묶음() {
            // 3개 시크릿이 동시에 위반 — 각 시크릿명이 모두 메시지에 포함되어야 함.
            // 한 시크릿당 여러 검증(길이/약한값/placeholder)이 동시 trigger되어 violation 총수 != 3일 수 있음.
            // 핵심은 "각 시크릿이 누락 없이 보고" + "한 번의 예외로 묶임".
            MockEnvironment env = validProdEnv()
                    .withProperty("CORS_ALLOWED_ORIGINS", "*")
                    .withProperty("DB_PASSWORD", "1234")
                    .withProperty("KAKAO_MAP_API_KEY", "your-key");

            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(ex -> {
                        String msg = ex.getMessage();
                        assertThat(msg).contains("SECRET_VALIDATION FAILED");
                        assertThat(msg).contains("CORS_ALLOWED_ORIGINS");
                        assertThat(msg).contains("DB_PASSWORD");
                        assertThat(msg).contains("KAKAO_MAP_API_KEY");
                        assertThat(msg).contains("docs/operations/secrets-catalog.md");
                    });
        }
    }

    @Nested
    class DB_USERNAME {

        @Test
        void 누락시_부팅_실패() {
            MockEnvironment env = validProdEnv().withProperty("DB_USERNAME", "");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_USERNAME");
        }

        @Test
        @DisplayName("DB_USERNAME이 'root'면 부팅 실패")
        void prodFails_whenDbUsernameIsRoot() {
            MockEnvironment env = validProdEnv();
            env.setProperty("DB_USERNAME", "root");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_USERNAME")
                    .hasMessageContaining("디폴트 계정명");
        }

        @Test
        @DisplayName("DB_USERNAME이 'admin'이면 부팅 실패")
        void prodFails_whenDbUsernameIsAdmin() {
            MockEnvironment env = validProdEnv();
            env.setProperty("DB_USERNAME", "admin");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_USERNAME");
        }

        @Test
        @DisplayName("DB_USERNAME이 spring.datasource.username으로만 채워져도 정상 통과")
        void prodPasses_whenDbUsernameOnlyInSpringKey() {
            MockEnvironment env = validProdEnv();
            env.setProperty("DB_USERNAME", "");
            env.setProperty("spring.datasource.username", "chunbae_prod");
            assertThatCode(() -> validator.validate(env)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DB_USERNAME이 placeholder 값(your-db-user)이면 부팅 실패")
        void prodFails_whenDbUsernameContainsPlaceholder() {
            MockEnvironment env = validProdEnv();
            env.setProperty("DB_USERNAME", "your-db-user");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_USERNAME")
                    .hasMessageContaining("placeholder");
        }

        @Test
        @DisplayName("DB_USERNAME이 replace-me placeholder면 부팅 실패")
        void prodFails_whenDbUsernameIsReplaceMe() {
            MockEnvironment env = validProdEnv();
            env.setProperty("DB_USERNAME", "replace-me");
            assertThatThrownBy(() -> validator.validate(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_USERNAME");
        }
    }

    @Nested
    class CORS_SPRING_KEY_FALLBACK {

        @Test
        @DisplayName("CORS_ALLOWED_ORIGINS 비어있어도 cors.allowed-origins로 채워지면 통과")
        void prodPasses_whenCorsOnlyInSpringKey() {
            MockEnvironment env = validProdEnv();
            env.setProperty("CORS_ALLOWED_ORIGINS", "");
            env.setProperty("cors.allowed-origins", "https://chunbae.tour,https://api.chunbae.tour");
            assertThatCode(() -> validator.validate(env)).doesNotThrowAnyException();
        }
    }
}
