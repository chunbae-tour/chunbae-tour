package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.UserMeHomeResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 홈 통합 응답 서비스 (Epic A S3, KAN-128).
 *
 * <p>{@code GET /api/v1/users/me/home}이 호출하는 service. AccountRepository + WalletRepository를
 * 조합해 한 번의 응답으로 프로필 + 엽전 잔액을 반환한다.
 *
 * <p>{@link UserMeService}와 분리한 이유:
 * <ul>
 *   <li>{@code UserMeService}는 Auth 도메인 단일 의존 ({@link AccountRepository}만) — 단순 유지.</li>
 *   <li>본 service는 Yeopjeon 도메인 {@link WalletRepository}에 의존 → 도메인 결합도가 다름.</li>
 *   <li>후속 위젯(찜/채팅/동행 등) 추가 시 본 service에 의존성이 누적될 예정 → 분리 유지가 정비 용이.</li>
 * </ul>
 *
 * <p>wallet fallback 정책: 사용자에게 wallet이 없으면 balance=0으로 응답.
 * 회원가입 후 wallet 생성 이벤트({@code UserRegisteredEvent} → {@code WalletEventListener})가 실패하거나
 * 늦게 처리되는 race가 있어도 home 호출이 500으로 깨지지 않도록 안전망. 실제 wallet 누락은 운영에서
 * 모니터링/배치로 별도 추적 (본 서비스의 책임 아님).
 */
@Service
@RequiredArgsConstructor
public class UserMeHomeService {

    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;

    /**
     * 마이페이지 홈 통합 응답.
     *
     * <p>{@code @Transactional(readOnly = true)} — 조회만이므로 readOnly. Account + Wallet 두 도메인
     * 조회를 한 트랜잭션으로 묶어 일관 시점의 데이터를 응답 (분리 시 race로 잔액 변동이 응답에 부분 반영될 위험).
     *
     * @param userId SecurityContext에서 추출한 본인 ID
     * @return profile + wallet 통합 응답
     * @throws BusinessException AUTH_006 — 사용자가 탈퇴/삭제 등으로 존재하지 않을 때
     */
    @Transactional(readOnly = true)
    public UserMeHomeResponse getHome(long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));

        // wallet 없는 신규 가입자 fallback — UserMeHomeResponse.of가 null을 balance=0으로 변환.
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);

        return UserMeHomeResponse.of(account, wallet);
    }
}
