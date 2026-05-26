package com.chunbaetour.domain.auth.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import java.util.Optional;

/**
 * 마이페이지 홈 통합 응답 (Epic A S3, KAN-128).
 *
 * <p>{@code GET /api/v1/users/me/home}의 응답 body. 클라이언트가 마이페이지 진입 시 한 번의 호출로
 * 프로필 + 엽전 잔액을 함께 받기 위한 통합 응답.
 *
 * <p><b>MVP 범위</b>: profile + wallet 2개 위젯만. 후속 위젯(찜한 관광지 카운트, 채팅방 활성 수,
 * 동행 신청 진행 수, 환불 진행 수 등)은 비즈니스 우선순위에 따라 별도 슬라이스에서 nested 객체로 추가.
 *
 * <p>profile은 {@link UserMeResponse}를 재사용 — {@code GET /api/v1/users/me} 응답과 동일한 스키마.
 * 클라이언트는 마이페이지 home에서도 동일한 profile 모델을 사용 가능 (DTO 분기 불필요).
 *
 * @param profile 사용자 프로필 (KAN-63 응답과 동일 필드)
 * @param wallet  엽전 wallet 정보 (잔액). 사용자에게 wallet이 없으면 balance=0으로 fallback.
 */
public record UserMeHomeResponse(
        UserMeResponse profile,
        WalletInfo wallet
) {

    /**
     * Wallet 응답 필드. balance만 노출.
     *
     * <p>운영상 wallet의 다른 필드(id, createdAt 등)는 클라이언트에 의미 없음 — 명시적 분리.
     * 추후 필요 시 wallet 응답을 확장하면 nested record가 깔끔.
     *
     * @param balance 현재 엽전 잔액 (음수 불가 — Wallet entity가 보장)
     */
    public record WalletInfo(long balance) {

        /** wallet이 존재하지 않는 신규 가입자 fallback. */
        public static WalletInfo empty() {
            return new WalletInfo(0L);
        }

        public static WalletInfo from(Wallet wallet) {
            return new WalletInfo(wallet.getBalance());
        }
    }

    /**
     * {@link Account}와 {@code Optional<Wallet>}로부터 통합 응답 생성. wallet이 빈 Optional이면 잔액 0으로 fallback.
     *
     * <p>fallback 사유: 회원가입 → UserRegisteredEvent → WalletEventListener가 wallet 생성하는 흐름에서
     * 이벤트 발행 실패 또는 race로 wallet 미생성 시 home 호출이 500 오류로 깨지지 않도록 안전망.
     *
     * <p>{@code Optional} 시그니처 채택: null 허용 메서드보다 빈/존재 상태가 호출자 코드에 명시적으로 드러남.
     */
    public static UserMeHomeResponse of(Account account, Optional<Wallet> wallet) {
        WalletInfo walletInfo = wallet.map(WalletInfo::from).orElseGet(WalletInfo::empty);
        return new UserMeHomeResponse(UserMeResponse.from(account), walletInfo);
    }
}
