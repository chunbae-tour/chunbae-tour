package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.PatchUserMeRequest;
import com.chunbaetour.domain.auth.dto.UserMeHomeResponse;
import com.chunbaetour.domain.auth.dto.UserMeResponse;
import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.security.JwtAuthenticationFilter;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.like.dto.response.UserLikedTargetResponse;
import com.chunbaetour.domain.like.service.UserLikedTargetService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.place.dto.response.UserReviewResponse;
import com.chunbaetour.domain.place.service.PlaceReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * USER 마이페이지 endpoint.
 *
 * <ul>
 *   <li>{@code GET /api/v1/users/me} — 본인 정보 조회 (Epic A S1, KAN-63)</li>
 *   <li>{@code PATCH /api/v1/users/me} — 닉네임/언어/프로필 partial update (Epic A S2, KAN-127)</li>
 *   <li>{@code GET /api/v1/users/me/home} — 마이페이지 홈 통합 응답 (Epic A S3, KAN-128)</li>
 *   <li>{@code GET /api/v1/users/me/likes} — 찜한 대상 페이징 (Epic A S5, KAN-130)</li>
 *   <li>{@code GET /api/v1/users/me/reviews} — 내가 작성한 리뷰 페이징 (Epic A S6, KAN-173)</li>
 *   <li>{@code DELETE /api/v1/users/me} — 회원 탈퇴 + 토큰 cascade 무효화 (Epic C S1, KAN-143)</li>
 * </ul>
 *
 * <p>URL 권한:
 * <ul>
 *   <li>{@code SecurityConfig}가 {@code /api/v1/users/**} = {@code hasRole(USER)}로 매핑.
 *       MERCHANT/ADMIN 토큰으로 호출 시 {@code AUTH_007}로 거부됨.</li>
 *   <li>비인증 호출은 {@code RestAuthenticationEntryPoint}가 {@code AUTH_006} 응답.</li>
 * </ul>
 *
 * <p>본인 식별: URL에 userId를 노출하지 않고 {@code @AuthenticationPrincipal Long userId}로 SecurityContext에서
 * 추출. 타인 정보 조회 차단의 핵심 — URL 조작으로 다른 사용자 정보를 절대 볼 수 없게 한다.
 *
 * <p><b>Epic A S4 (KAN-129)</b>: 임시 ping endpoint 제거됨. 인증/role 권한 매핑 검증은
 * {@link com.chunbaetour.domain.auth.MultiRoleAuthIntegrationTest}가 test scope의
 * {@code TestAuthFixtureController}를 사용해 커버. 시드 데이터 의존 없이 SecurityConfig 매핑만 검증.
 */
@Tag(name = "내 정보 (USER)", description = "본인 프로필 조회·수정·탈퇴, 찜/리뷰 목록 (/api/v1/users/me/**)")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserMeController {

    /**
     * GET /likes에서 클라이언트가 {@code ?sort=}로 지정 가능한 필드 화이트리스트.
     * 비허용 필드(예: password, id 이외 entity 내부 필드)는 INVALID_REQUEST로 거부 — 의도치 않은 정렬 노출 방지.
     */
    private static final Set<String> ALLOWED_LIKES_SORT_FIELDS = Set.of("createdAt", "id");

    /**
     * GET /reviews에서 클라이언트가 {@code ?sort=}로 지정 가능한 필드 화이트리스트.
     */
    private static final Set<String> ALLOWED_REVIEW_SORT_FIELDS = Set.of("createdAt", "id");

    private final UserMeService userMeService;
    private final UserMeHomeService userMeHomeService;
    private final UserLikedTargetService userLikedTargetService;
    private final PlaceReviewService placeReviewService;
    private final RefreshCookieFactory refreshCookieFactory;

    /**
     * 본인 정보 조회 (Epic A S1).
     *
     * @param userId SecurityContext에 저장된 본인 ID (JwtAuthenticationFilter가 채움)
     * @return 본인 마이페이지 정보 (민감 필드 제외)
     */
    @Operation(summary = "내 프로필 조회")
    @GetMapping
    public ApiResponse<UserMeResponse> getMe(@AuthenticationPrincipal Long userId) {
        // SecurityConfig hasRole(USER)로 보호되므로 정상 흐름에서는 null이 도달할 수 없지만,
        // 필터 우회/설정 누락 등 비정상 상태 방어 차원에서 명시적으로 확인.
        // long unboxing 시 NPE 대신 표준 AUTH_006 응답으로 통일.
        requireAuthenticated(userId);
        return ApiResponse.success(userMeService.getMe(userId));
    }

    /**
     * 본인 정보 partial update (Epic A S2, KAN-127).
     *
     * <p>{@link PatchUserMeRequest}의 nickname/language/profileImageUrl 중 보낸 필드만 갱신.
     * URL에 userId 노출 X — {@link AuthenticationPrincipal}로 SecurityContext에서 추출해 PK 변조 원천 차단.
     *
     * @param userId  SecurityContext에 저장된 본인 ID
     * @param request partial update 요청 (모든 필드 optional)
     * @return 갱신 후 사용자 정보 (GET /me와 동일 포맷)
     */
    @Operation(summary = "내 프로필 수정")
    @PatchMapping
    public ApiResponse<UserMeResponse> updateMe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PatchUserMeRequest request) {
        requireAuthenticated(userId);
        return ApiResponse.success(userMeService.updateMe(userId, request));
    }

    /**
     * 마이페이지 홈 통합 응답 (Epic A S3, KAN-128).
     *
     * <p>프로필 + 엽전 잔액을 한 번에 응답 — 클라이언트가 마이페이지 진입 시 N개 API 호출의 latency 누적 회피.
     * MVP는 profile + wallet만. 위젯(찜/채팅/동행 등)은 후속 슬라이스에서 응답에 nested로 추가.
     *
     * @param userId SecurityContext에 저장된 본인 ID
     * @return 프로필 + wallet 통합 응답
     */
    @Operation(summary = "마이페이지 홈")
    @GetMapping("/home")
    public ApiResponse<UserMeHomeResponse> getHome(@AuthenticationPrincipal Long userId) {
        requireAuthenticated(userId);
        return ApiResponse.success(userMeHomeService.getHome(userId));
    }

    /**
     * 본인이 찜한 대상 목록 페이징 조회 (Epic A S5, KAN-130).
     *
     * <p>{@link UserLikedTargetService#getLikedTargets}를 호출 — 본 컨트롤러는
     * 인증된 userId 추출 + 페이징 파라미터 전달만 담당. 응답 DTO({@link UserLikedTargetResponse})는
     * PLACE, MARKET, FESTIVAL 카드에 필요한 공통 필드를 제공한다.
     *
     * <p>페이징 정책:
     * <ul>
     *   <li>default size = 20</li>
     *   <li>max size = 100 (운영 부하 방지 — {@code UserLikedTargetService.getLikedTargets} 내부 가드)</li>
     *   <li>default sort = {@code createdAt DESC} — 최근 찜 순. 클라이언트가 {@code ?sort=}로 오버라이드 가능.
     *       허용 필드: {@code createdAt}, {@code id}. 그 외 필드 지정 시 {@link ErrorCode#INVALID_REQUEST} (COMMON_002) 응답.</li>
     * </ul>
     *
     * <p><b>보안 회귀 가드</b>: PathVariable {@code userId} 미사용 — SecurityContext userId만 사용해
     * 다른 사용자의 찜 목록을 절대 조회하지 못한다.
     *
     * @param userId   SecurityContext에 저장된 본인 ID
     * @param pageable {@code ?page=0&size=20} 파라미터 — Spring Data Pageable 자동 바인딩
     * @return 찜한 대상 페이지 (UserLikedTargetResponse 페이징)
     */
    @Operation(summary = "내 찜 목록 조회")
    @GetMapping("/likes")
    public ApiResponse<Page<UserLikedTargetResponse>> getLikedPlaces(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회할 찜 타입. PLACE, MARKET, FESTIVAL을 지원합니다.")
            @RequestParam(defaultValue = "PLACE") LikeTargetType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        requireAuthenticated(userId);
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_LIKES_SORT_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        });

        return ApiResponse.success(userLikedTargetService.getLikedTargets(userId, type, pageable));
    }

    /**
     * 본인이 작성한 관광지 리뷰 목록 페이징 조회 (Epic A S6, 4-6).
     *
     * <p>마이페이지에서 노출되는 리뷰 목록. 최신순 정렬 기본 적용.
     *
     * @param userId   SecurityContext에 저장된 본인 ID
     * @param pageable 페이징 파라미터
     * @return 작성한 리뷰 목록 페이지
     */
    @Operation(summary = "내 리뷰 목록 조회")
    @GetMapping("/reviews")
    public ApiResponse<Page<UserReviewResponse>> getMyReviews(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 10, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        requireAuthenticated(userId);
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_REVIEW_SORT_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        });
        return ApiResponse.success(placeReviewService.getUserReviews(userId, pageable));
    }

    /**
     * 회원 탈퇴 (Epic C S1, KAN-143).
     *
     * <p>DELETE {@code /api/v1/users/me} — soft delete + 토큰 cascade 무효화 + Refresh cookie 만료.
     *
     * <p>흐름:
     * <ol>
     *   <li>SecurityConfig가 {@code /api/v1/users/**} = {@code hasRole(USER)}로 매핑 — 미인증 호출은
     *       {@link com.chunbaetour.domain.auth.security.RestAuthenticationEntryPoint}가 AUTH_006 응답.</li>
     *   <li>{@link JwtAuthenticationFilter}가 Access Token 검증을 마치고 {@link AccessClaims}를 request
     *       attribute({@link JwtAuthenticationFilter#REQUEST_ATTR_ACCESS_CLAIMS})에 노출 → JWT 재파싱 없음.</li>
     *   <li>{@link UserMeService#deleteMe}가 softDelete + afterCommit 토큰 cascade 등록.</li>
     *   <li>응답: 204 No Content + {@link RefreshCookieFactory#expire}로 Refresh Cookie 만료(Max-Age=0).</li>
     * </ol>
     *
     * <p><b>보안 회귀 가드 — PathVariable 미사용</b>: 본인 식별은 {@code @AuthenticationPrincipal Long userId}로
     * SecurityContext에서만 추출. URL에 {@code /users/{id}}처럼 PK를 노출하면 다른 사용자 계정 탈퇴를
     * 시도할 수 있어 원천 차단. PATCH /me / GET /me / DELETE /me 모두 같은 정책.
     *
     * <p><b>보안 회귀 가드 — Set-Cookie 만료 처리</b>: 클라이언트의 Refresh Cookie를 즉시 제거해야 같은
     * 브라우저에서 후속 reissue 호출이 발생하지 않는다. {@link RefreshCookieFactory#expire}가 발급 시와
     * 동일한 {@code name}/{@code path}/{@code SameSite}/{@code Secure} 속성으로 Max-Age=0 쿠키를 만들어
     * 브라우저가 같은 쿠키로 인식해 덮어쓴다 — KAN-125 SameSite/Domain 정책 자동 준수.
     *
     * <p><b>중복 호출 처리</b>: 첫 탈퇴 직후 access token이 blacklist에 등록되므로 같은 토큰으로 재호출
     * 시 {@link JwtAuthenticationFilter}가 AUTH_013으로 차단한다. {@code Account.softDelete}의
     * {@link IllegalStateException} 가드는 도메인 단위 테스트가 커버 — 통합 흐름에서는 도달 불가.
     *
     * @param userId  SecurityContext에서 추출한 본인 ID
     * @param request 인증 필터가 채운 {@link AccessClaims}를 attribute에서 꺼내기 위해 받음 (JWT 재파싱 회피)
     * @return 204 No Content + Set-Cookie 만료 헤더
     */
    @Operation(summary = "회원 탈퇴")
    @DeleteMapping
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        requireAuthenticated(userId);

        Object attr = request.getAttribute(JwtAuthenticationFilter.REQUEST_ATTR_ACCESS_CLAIMS);
        if (!(attr instanceof AccessClaims claims)) {
            // 인증 필터를 통과했는데 attribute가 없는 비정상 상태(필터 설정 누락/우회 등). 안전하게 거부.
            // 정상 흐름에서는 SecurityContext와 attribute가 같은 분기에서 채워지므로 도달 불가.
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        userMeService.deleteMe(userId, claims);

        // 클라이언트의 Refresh Cookie를 즉시 만료 (Max-Age=0). 발급 시와 같은 name/path여야 브라우저가 덮어쓴다.
        // LogoutController와 동일한 RefreshCookieFactory.expire() 재사용 — KAN-125 SameSite/Domain 정책 자동 준수.
        ResponseCookie expiredCookie = refreshCookieFactory.expire();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    /**
     * SecurityContext에 userId가 채워지지 않은 비정상 상태를 표준 AUTH_006 에러로 변환.
     *
     * <p>본 컨트롤러의 모든 endpoint는 인증 필수이므로 userId null = 인증 실패와 동일하게 응답.
     * NPE를 던지지 않고 도메인 에러로 통일하여 클라이언트 응답 형식 일관성 유지.
     */
    private static void requireAuthenticated(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
