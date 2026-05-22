package com.chunbaetour.domain.common.secret;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * 운영(prod) 프로파일 부팅 시 시크릿/환경변수 카탈로그 기반 검증.
 *
 * <p>실행 시점: {@link ApplicationEnvironmentPreparedEvent} — 빈 생성 전, Environment 준비 직후.
 * 이 시점에 실패시키면 DataSource/Redis 커넥션 시도보다 먼저 명확한 메시지로 부팅 차단 가능.
 *
 * <p>등록 방식: Spring 빈으로 자동 스캔되지 않으므로 {@code META-INF/spring/org.springframework.context.ApplicationListener.imports}
 * 파일에 클래스 FQN을 등록한다.
 *
 * <p>검증 정책은 {@code docs/operations/secrets-catalog.md} + {@code docs/adr/0002-secret-injection-standard.md}
 * 와 동기 유지. 카탈로그에 시크릿을 추가하면 본 클래스의 검증 항목도 함께 갱신해야 한다.
 *
 * <p>검증 실패 시: 모든 위반 사항을 수집해 한 번에 {@link IllegalStateException}으로 throw — 부팅 즉시 실패 +
 * 운영자가 한 번의 부팅 시도로 모든 누락/오류를 파악 가능.
 *
 * <p>JWT_SECRET, CORS allowed-origins 등 @ConfigurationProperties record가 자체 검증하는 항목은 본 validator에서
 * 중복 검증하지 않는다 (단일 검증 책임). 본 validator는 운영 인프라성 env var (DB_PASSWORD, KAKAO_MAP_API_KEY,
 * PORTONE_*)와 카탈로그 정합성에 집중.
 */
public class SecretValidator implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** 활성화 프로파일 — 본 validator는 prod에서만 동작. local/test는 더미 값 허용. */
    private static final String ACTIVE_PROFILE = "prod";

    /** DB 비밀번호 최소 길이 — 카탈로그 §데이터베이스/캐시 §DB_PASSWORD. */
    private static final int MIN_DB_PASSWORD_LENGTH = 12;

    /**
     * 평문 디폴트/예제 비밀번호 차단 목록. 누구나 추측 가능한 값은 부팅 거부.
     * 소문자 비교.
     *
     * <p><b>한계</b>: 완전 일치만 차단. {@code password123}, {@code admin2026}처럼
     * 약한 패턴에 숫자/특수문자를 붙인 변형은 탐지 안 됨. 운영 정책상 허용 수준으로 판단.
     * 강한 검증 필요 시 zxcvbn 같은 패턴 기반 라이브러리 도입 별도 검토.
     */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "1234", "12345", "123456", "1234567", "12345678",
            "password", "passwd", "root", "admin", "administrator", "test", "qwerty"
    );

    /**
     * placeholder 토큰 — .env.example 더미 값이 prod로 그대로 흘러가는 사고 방지.
     * 시크릿 값에 이 토큰이 포함되면 부팅 거부. 소문자 비교.
     */
    private static final List<String> PLACEHOLDER_TOKENS = List.of(
            "your-", "your_", "replace-me", "replace_me", "placeholder",
            "xxxxxxxx", "xxxxx", "todo", "fixme", "changeme", "example"
    );

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        validate(event.getEnvironment());
    }

    /**
     * 테스트 시드: ApplicationEvent 객체 생성 없이 직접 Environment를 주입해 검증 로직만 실행.
     * 운영 코드에서 직접 호출 금지.
     */
    void validate(Environment env) {
        if (!isProdActive(env)) {
            return;
        }

        List<String> violations = new ArrayList<>();

        // CORS — 정책상 prod 필수. 와일드카드 차단 + URL 형식 검증.
        validateCorsOrigins(env, violations);

        // DB — username/password. host/name은 누락 시 spring boot가 알아서 fail이라 별도 검증 생략.
        validateDbPassword(env, violations);
        validateDbUsername(env, violations);

        // 외부 API 키 — placeholder 차단 + 비어있지 않음.
        validateExternalKey(env, "KAKAO_MAP_API_KEY", "kakao.api.key", violations);
        validateExternalKey(env, "PORTONE_SECRET", "portone.secret", violations);
        validateExternalKey(env, "PORTONE_STORE_ID", "portone.store-id", violations);
        validateExternalKey(env, "PORTONE_WEBHOOK_SECRET", "portone.webhook-secret", violations);
        validateExternalKey(env, "PORTONE_CHANNEL_CARD", "portone.channel.card", violations);
        validateExternalKey(env, "PORTONE_CHANNEL_KAKAO_PAY", "portone.channel.kakao-pay", violations);
        validateExternalKey(env, "PORTONE_CHANNEL_TOSS_PAY", "portone.channel.toss-pay", violations);
        validateExternalKey(env, "PORTONE_CHANNEL_FOREIGN_CARD", "portone.channel.foreign-card", violations);

        if (!violations.isEmpty()) {
            String summary = "SECRET_VALIDATION FAILED (prod 프로파일): "
                    + violations.size() + "건의 시크릿이 카탈로그 정합성 검증을 통과하지 못했습니다.\n  - "
                    + String.join("\n  - ", violations)
                    + "\n\ndocs/operations/secrets-catalog.md 참조.";
            throw new IllegalStateException(summary);
        }
    }

    /** prod 프로파일 활성화 여부. {@code spring.profiles.active}가 콤마로 여러 개일 수 있음. */
    private boolean isProdActive(Environment env) {
        String[] active = env.getActiveProfiles();
        for (String p : active) {
            if (ACTIVE_PROFILE.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * CORS_ALLOWED_ORIGINS 검증:
     * <ol>
     *   <li>비어있지 않음</li>
     *   <li>와일드카드(*) 미포함 — Refresh Cookie credential 호환성</li>
     *   <li>각 origin이 절대 URL 형식 (스킴+호스트). 패스/쿼리 미포함</li>
     * </ol>
     */
    private void validateCorsOrigins(Environment env, List<String> violations) {
        String raw = env.getProperty("CORS_ALLOWED_ORIGINS");
        if (raw == null || raw.isBlank()) {
            raw = env.getProperty("cors.allowed-origins");
        }
        if (raw == null || raw.isBlank()) {
            violations.add("CORS_ALLOWED_ORIGINS: 비어있음 (prod는 명시적 origin 필수)");
            return;
        }
        if (raw.contains("*")) {
            violations.add("CORS_ALLOWED_ORIGINS: 와일드카드(*) 포함됨 — allowCredentials=true와 호환 불가");
        }
        String[] origins = raw.split(",");
        for (String origin : origins) {
            String trimmed = origin.trim();
            if (trimmed.isEmpty()) {
                violations.add("CORS_ALLOWED_ORIGINS: 빈 항목 포함 (콤마 구분 오류)");
                continue;
            }
            if (!isValidOriginFormat(trimmed)) {
                violations.add("CORS_ALLOWED_ORIGINS: '" + trimmed
                        + "' — 스킴+호스트 형식이 아닙니다 (예: https://example.com)");
            }
        }
    }

    /**
     * Origin은 스킴(http/https) + 호스트(+ 선택 포트)만 허용. path/query 부착 금지.
     */
    private boolean isValidOriginFormat(String origin) {
        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return false;
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return false;
            }
            // origin은 path/query/fragment를 포함하면 안 됨
            if (uri.getPath() != null && !uri.getPath().isEmpty()) {
                return false;
            }
            if (uri.getQuery() != null) {
                return false;
            }
            if (uri.getFragment() != null) {
                return false;
            }
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * DB_PASSWORD 검증: 비어있지 않음 + 최소 12자 + 약한 비밀번호(평문 디폴트) 차단.
     *
     * <p>실제 키 매핑은 {@code spring.datasource.password}. env var 우선순위로 환경변수가 채워지면
     * 같은 값을 둘 다에서 읽을 수 있어 환경변수 명을 그대로 검증.
     */
    private void validateDbPassword(Environment env, List<String> violations) {
        String pw = env.getProperty("DB_PASSWORD");
        if (pw == null || pw.isBlank()) {
            // spring.datasource.password로도 시도 (yml에 직접 박힌 경우 대응)
            pw = env.getProperty("spring.datasource.password");
        }
        if (pw == null || pw.isBlank()) {
            violations.add("DB_PASSWORD: 비어있음");
            return;
        }
        if (pw.length() < MIN_DB_PASSWORD_LENGTH) {
            violations.add("DB_PASSWORD: 최소 " + MIN_DB_PASSWORD_LENGTH + "자 필요 (현재 " + pw.length() + "자)");
        }
        String lower = pw.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(lower)) {
            violations.add("DB_PASSWORD: 약한 비밀번호 차단 목록과 일치 (평문 디폴트 추정)");
        }
        if (containsPlaceholder(lower)) {
            violations.add("DB_PASSWORD: placeholder 패턴 포함 (.env.example 더미 값으로 추정)");
        }
    }

    /**
     * DB_USERNAME 검증: 비어있지 않음 + 디폴트 계정명(root, admin 등) 차단 + placeholder 패턴 차단.
     *
     * <p>WEAK_PASSWORDS 차단 목록을 재사용해 root/admin/administrator/test 같은
     * 디폴트 계정명이 그대로 운영에 올라가는 것을 부팅 시점에 차단한다.
     * DB_PASSWORD와 일관된 정책.
     *
     * <p>{@code your-db-user}, {@code replace-me} 같은 .env.example 더미 값이 prod로 흘러가는
     * 사고도 placeholder 패턴 차단으로 막는다 (DB_PASSWORD/외부 키 검증과 일관).
     */
    private void validateDbUsername(Environment env, List<String> violations) {
        String username = env.getProperty("DB_USERNAME");
        if (username == null || username.isBlank()) {
            username = env.getProperty("spring.datasource.username");
        }
        if (username == null || username.isBlank()) {
            violations.add("DB_USERNAME: 비어있음");
            return;
        }
        String lower = username.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(lower)) {
            violations.add("DB_USERNAME: 디폴트 계정명 차단 목록과 일치 (root/admin 등)");
        }
        if (containsPlaceholder(lower)) {
            violations.add("DB_USERNAME: placeholder 패턴 포함 (.env.example 더미 값으로 추정)");
        }
    }

    /**
     * 외부 API 키 검증: 비어있지 않음 + placeholder 패턴 차단.
     *
     * <p>외부 발급 키는 형식이 다양해 길이/패턴 강제 검증은 안 함. .env.example의 더미 값
     * ({@code your-*}, {@code xxxxxxxx} 등)이 그대로 prod로 흘러가는 사고만 방지.
     */
    private void validateExternalKey(Environment env, String envVarName, String springKeyName, List<String> violations) {
        String v = env.getProperty(envVarName);
        if (v == null || v.isBlank()) {
            v = env.getProperty(springKeyName);
        }
        if (v == null || v.isBlank()) {
            violations.add(envVarName + ": 비어있음");
            return;
        }
        if (containsPlaceholder(v.toLowerCase(Locale.ROOT))) {
            violations.add(envVarName + ": placeholder 패턴 포함 (.env.example 더미 값으로 추정)");
        }
    }

    private boolean containsPlaceholder(String lowerValue) {
        for (String token : PLACEHOLDER_TOKENS) {
            if (lowerValue.contains(token)) {
                return true;
            }
        }
        return false;
    }

    // 테스트 가시성을 위해 노출. 운영 코드에서 사용 금지.
    static List<String> placeholderTokensForTest() {
        return List.copyOf(PLACEHOLDER_TOKENS);
    }

    static List<String> weakPasswordsForTest() {
        return List.copyOf(WEAK_PASSWORDS);
    }
}
