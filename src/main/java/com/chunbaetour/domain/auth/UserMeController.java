package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.PatchUserMeRequest;
import com.chunbaetour.domain.auth.dto.UserMeHomeResponse;
import com.chunbaetour.domain.auth.dto.UserMeResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.response.UserLikedPlaceResponse;
import com.chunbaetour.domain.place.service.PlaceLikeService;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * USER 마이페이지 endpoint.
 *
 * <ul>
 *   <li>{@code GET /api/v1/users/me} — 본인 정보 조회 (Epic A S1, KAN-63)</li>
 *   <li>{@code PATCH /api/v1/users/me} — 닉네임/언어/프로필 partial update (Epic A S2, KAN-127)</li>
 *   <li>{@code GET /api/v1/users/me/home} — 마이페이지 홈 통합 응답 (Epic A S3, KAN-128)</li>
 *   <li>{@code GET /api/v1/users/me/likes} — 찜한 관광지 페이징 (Epic A S5, KAN-130)</li>
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
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserMeController {

    /**
     * GET /likes에서 클라이언트가 {@code ?sort=}로 지정 가능한 필드 화이트리스트.
     * 비허용 필드(예: password, id 이외 entity 내부 필드)는 INVALID_REQUEST로 거부 — 의도치 않은
     * 정렬 노출 방지.
     */
    private static final Set<String> ALLOWED_LIKES_SORT_FIELDS = Set.of("createdAt", "id");

    private final UserMeService userMeService;
    private final UserMeHomeService userMeHomeService;
    private final PlaceLikeService placeLikeService;

    /**
     * 본인 정보 조회 (Epic A S1).
     *
     * @param userId SecurityContext에 저장된 본인 ID (JwtAuthenticationFilter가 채움)
     * @return 본인 마이페이지 정보 (민감 필드 제외)
     */
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
    @GetMapping("/home")
    public ApiResponse<UserMeHomeResponse> getHome(@AuthenticationPrincipal Long userId) {
        requireAuthenticated(userId);
        return ApiResponse.success(userMeHomeService.getHome(userId));
    }

    /**
     * 본인이 찜한 관광지 목록 페이징 조회 (Epic A S5, KAN-130).
     *
     * <p>KAN-124 (PR #163)가 제공한 {@link PlaceLikeService#getUserLikedPlaces}를 호출 — 본 컨트롤러는
     * 인증된 userId 추출 + 페이징 파라미터 전달만 담당. 응답 DTO({@link UserLikedPlaceResponse})는
     * Place 도메인 모델 그대로 노출 — 마이페이지 전용 별도 스키마를 두지 않아 클라이언트가 단일 모델 사용.
     *
     * <p>페이징 정책:
     * <ul>
     *   <li>default size = 20</li>
     *   <li>max size = 100 (운영 부하 방지 — {@code PlaceLikeService.getUserLikedPlaces} 내부 가드)</li>
     *   <li>default sort = {@code createdAt DESC} — 최근 찜 순. 클라이언트가 {@code ?sort=}로 오버라이드 가능.
     *       허용 필드: {@code createdAt}, {@code id}. 그 외 필드 지정 시 {@link ErrorCode#INVALID_REQUEST} (COMMON_002) 응답.</li>
     * </ul>
     *
     * <p><b>보안 회귀 가드</b>: PathVariable {@code userId} 미사용 — SecurityContext userId만 사용해
     * 다른 사용자의 찜 목록을 절대 조회하지 못한다.
     *
     * @param userId   SecurityContext에 저장된 본인 ID
     * @param pageable {@code ?page=0&size=20} 파라미터 — Spring Data Pageable 자동 바인딩
     * @return 찜한 관광지 페이지 (UserLikedPlaceResponse 페이징)
     */
    @GetMapping("/likes")
    public ApiResponse<Page<UserLikedPlaceResponse>> getLikedPlaces(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        requireAuthenticated(userId);
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_LIKES_SORT_FIELDS.contains(order.getProperty())) {
                // 화이트리스트 외 필드(예: ?sort=password) — 의도치 않은 entity 내부 필드 정렬 차단
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        });
        return ApiResponse.success(placeLikeService.getUserLikedPlaces(userId, pageable));
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
