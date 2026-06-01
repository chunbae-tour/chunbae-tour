package com.chunbaetour.domain.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // ===== COMMON =====
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_001", "서버 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "잘못된 요청입니다."),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST,           "COMMON_003", "필수 입력값이 누락되었습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST,              "COMMON_004", "입력값이 유효하지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND,                 "COMMON_005", "요청한 리소스를 찾을 수 없습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS,          "COMMON_006", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    EXTERNAL_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE,   "COMMON_007", "외부 서비스 연동 중 오류가 발생했습니다."),
    // cursor 값이 Base64 디코딩 실패 또는 1 미만(IDENTITY id 범위 위반)일 때 사용
    INVALID_CURSOR(HttpStatus.BAD_REQUEST,                   "COMMON_008", "유효하지 않은 커서 값입니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT,                   "COMMON_009", "동시 수정 충돌이 발생했습니다. 다시 시도해주세요."),
    // 페이지네이션 공통 검증 — 결제 외 도메인도 동일 기준 적용
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST,                "COMMON_010", "페이지 크기는 1 이상 100 이하여야 합니다."),

    // ===== AUTH (담당: 정민교) =====
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Access 토큰이 만료되었습니다."),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_003", "Access 토큰이 유효하지 않습니다."),
    // AUTH_004: Refresh 토큰 자체의 만료 (exp claim 기준). 클라이언트는 재로그인 안내 필요.
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_004", "Refresh 토큰이 만료되었습니다."),
    // AUTH_005: 서명 오류/변조, Redis 미존재(이미 회전됨/로그아웃됨), 강제 무효화 등 "유효하지 않은 Refresh" 모두 포함.
    //          탈취 정황을 클라이언트에 노출하지 않기 위해 사유를 세분화하지 않는다 (보안 정책).
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_005", "Refresh 토큰이 유효하지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_006", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_007", "권한이 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_008", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "AUTH_009", "이미 사용 중인 닉네임입니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "AUTH_010", "비밀번호 형식이 올바르지 않습니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "AUTH_011", "이메일 형식이 올바르지 않습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "AUTH_012", "정지된 계정입니다."),
    // AUTH_013: 로그아웃 시 Access Token이 블랙리스트에 등록되며, 이후 같은 토큰으로 요청하면 거부된다.
    //           남은 만료 시간 동안만 블랙리스트에 머무르므로 자연 만료 후에는 같은 tokenId가 새로 발급될 가능성도 사라진다 (UUID).
    BLACKLISTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_013", "로그아웃된 토큰입니다."),
    // AUTH_014: IP 기반 rate limit 초과 (sa-docs/11 운영 보안 정책 §Rate Limit).
    //           회원가입 3회/10분/IP, 로그인 5회/분/IP 등 endpoint별 정책 초과 시 응답.
    //           응답에 Retry-After 헤더 + X-RateLimit-Limit/Remaining 헤더 첨부 (REST 표준).
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_014", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_015", "존재하지 않는 사용자입니다."),

    // ===== COMMUNITY (담당: 박경화) =====
    POST_NOT_FOUND(HttpStatus.NOT_FOUND,                "COMMUNITY_001", "존재하지 않는 게시글입니다."),
    // COMMUNITY_002: 예약
    POST_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN,         "COMMUNITY_003", "해당 게시글을 수정할 권한이 없습니다."),
    POST_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN,         "COMMUNITY_004", "해당 게시글을 삭제할 권한이 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND,             "COMMUNITY_005", "존재하지 않는 댓글입니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN,             "COMMUNITY_006", "댓글 작성자만 수정·삭제할 수 있습니다."),
    COMMENT_ALREADY_DELETED(HttpStatus.BAD_REQUEST,      "COMMUNITY_007", "이미 삭제된 댓글입니다."),
    COMMENT_REPLY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST,"COMMUNITY_008", "대댓글에는 답글을 달 수 없습니다."),
    POST_NOT_COMMENTABLE(HttpStatus.FORBIDDEN,          "COMMUNITY_009", "댓글을 작성할 수 없는 게시글입니다."),

    // ===== PLACE (담당: 김인목) =====
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND,                   "PLACE_001", "존재하지 않는 관광지입니다."),
    MARKET_NOT_FOUND(HttpStatus.NOT_FOUND,                  "PLACE_002", "존재하지 않는 전통시장입니다."),
    FESTIVAL_NOT_FOUND(HttpStatus.NOT_FOUND,                "PLACE_003", "존재하지 않는 축제입니다."),
    INVALID_LOCATION(HttpStatus.BAD_REQUEST,                "PLACE_004", "위치 정보가 올바르지 않습니다."),
    SEARCH_KEYWORD_TOO_SHORT(HttpStatus.BAD_REQUEST,        "PLACE_005", "검색어는 최소 1자 이상 입력해주세요."),
    SEARCH_KEYWORD_TOO_LONG(HttpStatus.BAD_REQUEST,         "PLACE_006", "검색어는 최대 50자까지 입력 가능합니다."),
    MAP_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PLACE_007", "길찾기 서비스를 일시적으로 사용할 수 없습니다."),
    INVALID_SEARCH_RADIUS(HttpStatus.BAD_REQUEST,           "PLACE_008", "유효하지 않은 반경 범위입니다. (최대 20km)"),
    SEARCH_INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST,       "PLACE_009", "검색 시작일은 종료일보다 늦을 수 없습니다."),
    LIKE_ALREADY_EXISTS(HttpStatus.CONFLICT,                "PLACE_010", "이미 찜한 관광지입니다."),
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND,                  "PLACE_011", "찜하지 않은 관광지입니다."),

    // ===== PAY (담당: 신현민) =====
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST,            "PAY_001", "엽전 잔액이 부족합니다."),
    CHARGE_AMOUNT_TOO_LOW(HttpStatus.BAD_REQUEST,           "PAY_002", "충전 금액은 5,000원 이상이어야 합니다."),
    INVALID_CHARGE_UNIT(HttpStatus.BAD_REQUEST,             "PAY_003", "충전 금액은 1,000원 단위로 입력해주세요."),
    CHARGE_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST,          "PAY_004", "1회 최대 충전 금액은 100,000원입니다."),
    PAYMENT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PAY_005", "결제 서비스를 일시적으로 사용할 수 없습니다."),
    PAYMENT_CANCELLED(HttpStatus.BAD_REQUEST,               "PAY_006", "결제가 취소되었습니다."),
    DUPLICATE_PAYMENT_REQUEST(HttpStatus.CONFLICT,          "PAY_007", "이미 처리된 결제 요청입니다."),
    PAYMENT_PROCESSING(HttpStatus.SERVICE_UNAVAILABLE,      "PAY_008", "결제 처리 중입니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND,         "PAY_009", "존재하지 않는 결제 내역입니다."),
    REFUND_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST,           "PAY_010", "환불 가능한 기간이 지났습니다."),
    PAYMENT_HISTORY_FORBIDDEN(HttpStatus.FORBIDDEN,         "PAY_011", "본인의 결제 내역만 조회할 수 있습니다."),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND,                  "PAY_012", "엽전 지갑을 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST,         "PAY_013", "결제 금액이 일치하지 않습니다."),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED,      "PAY_014", "웹훅 서명이 유효하지 않습니다."),
    REFUND_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST,             "PAY_015", "완료된 결제만 환불 요청할 수 있습니다."),
    DUPLICATE_REFUND_REQUEST(HttpStatus.CONFLICT,           "PAY_016", "이미 환불 요청이 진행 중인 주문입니다."),
    REFUND_BALANCE_INSUFFICIENT(HttpStatus.BAD_REQUEST,     "PAY_017", "환불에 필요한 엽전 잔액이 부족합니다."),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND,                  "PAY_018", "존재하지 않는 환불 요청입니다."),
    REFUND_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST,       "PAY_019", "대기 중인 환불 요청만 취소할 수 있습니다."),
    REFUND_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT,  "PAY_020", "현재 상태에서는 해당 작업을 수행할 수 없습니다."),
    DUPLICATE_QR_PAY_REQUEST(HttpStatus.CONFLICT,          "PAY_022", "이미 대기 중인 QR 결제 요청이 있습니다."),
    SELF_PAYMENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST,       "PAY_023", "본인 가게에는 결제할 수 없습니다."),
    ZERO_AMOUNT_NOT_ALLOWED(HttpStatus.BAD_REQUEST,        "PAY_024", "결제 금액은 0원보다 커야 합니다."),
    QR_PAY_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT,  "PAY_025", "현재 상태에서는 해당 작업을 수행할 수 없습니다."),
    QR_PAY_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND,         "PAY_026", "존재하지 않는 QR 결제 요청입니다."),
    SHOP_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND,            "PAY_027", "상인 엽전 지갑을 찾을 수 없습니다."),
    QR_PAY_CONFIRM_FORBIDDEN(HttpStatus.FORBIDDEN,         "PAY_028", "본인 가게의 결제 요청만 승인/거절할 수 있습니다."),

    // ===== STORE (담당: 신현민) =====
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND,                 "STORE_001", "존재하지 않는 상품입니다."),
    PRODUCT_SOLD_OUT(HttpStatus.CONFLICT,                   "STORE_002", "품절된 상품입니다."),
    INVALID_PURCHASE_QUANTITY(HttpStatus.BAD_REQUEST,       "STORE_003", "구매 수량은 1개 이상이어야 합니다."),
    PURCHASE_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST,      "STORE_004", "1회 최대 구매 수량을 초과했습니다."),
    PURCHASE_PROCESSING(HttpStatus.SERVICE_UNAVAILABLE,     "STORE_005", "구매 처리 중입니다. 잠시 후 다시 시도해주세요."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND,                   "STORE_006", "존재하지 않는 주문입니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST,         "STORE_007", "이미 취소된 주문입니다."),

    // ===== MERCHANT (담당: 신현민) =====
    MERCHANT_CERT_ALREADY_PENDING(HttpStatus.CONFLICT,      "MERCHANT_001", "이미 상인 인증 신청이 진행 중입니다."),
    INVALID_BUSINESS_NUMBER(HttpStatus.BAD_REQUEST,         "MERCHANT_002", "유효하지 않은 사업자등록번호입니다."),
    MERCHANT_NOT_CERTIFIED(HttpStatus.FORBIDDEN,            "MERCHANT_003", "상인 인증이 필요합니다."),
    DUPLICATE_BUSINESS_NUMBER(HttpStatus.CONFLICT,          "MERCHANT_004", "이미 등록된 사업자등록번호입니다."),
    MERCHANT_APPLICATION_STATUS_INVALID(HttpStatus.CONFLICT,"MERCHANT_005", "현재 상태에서는 허용되지 않는 작업입니다."),
    MERCHANT_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND,    "MERCHANT_006", "존재하지 않는 상인 신청입니다."),

    // ===== SHOP (담당: 신현민) =====
    SHOP_NOT_FOUND(HttpStatus.NOT_FOUND,                    "SHOP_001", "존재하지 않는 가게입니다."),
    SHOP_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN,             "SHOP_002", "본인 가게 정보만 수정할 수 있습니다."),
    SHOP_ALREADY_EXISTS(HttpStatus.CONFLICT,                "SHOP_003", "이미 등록된 가게가 있습니다."),
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND,                    "SHOP_004", "존재하지 않는 메뉴입니다."),
    SHOP_INACTIVE(HttpStatus.FORBIDDEN,                     "SHOP_005", "정지 또는 폐업 상태의 가게입니다."),
    MENU_DUPLICATE(HttpStatus.CONFLICT,                     "SHOP_006", "이미 동일한 이름의 메뉴가 존재합니다."),
    MENU_UNAVAILABLE(HttpStatus.CONFLICT,                   "SHOP_007", "현재 주문할 수 없는 메뉴입니다."),
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND,              "SHOP_008", "존재하지 않는 정산 요청입니다."),
    DUPLICATE_SETTLEMENT_REQUEST(HttpStatus.CONFLICT,       "SHOP_009", "이미 처리 대기 중인 정산 요청이 있습니다."),
    SETTLEMENT_INVALID_STATUS(HttpStatus.CONFLICT,          "SHOP_010", "현재 상태에서는 처리할 수 없는 정산 요청입니다."),
    SETTLEMENT_BALANCE_EMPTY(HttpStatus.BAD_REQUEST,        "SHOP_011", "정산 가능한 잔액이 없습니다."),
    SETTLEMENT_AMOUNT_TOO_LOW(HttpStatus.BAD_REQUEST,       "SHOP_012", "정산 신청 최소 금액은 5,000엽전입니다."),
    AD_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND,          "SHOP_013", "존재하지 않는 광고 신청입니다."),
    DUPLICATE_AD_APPLICATION(HttpStatus.CONFLICT,           "SHOP_014", "이미 처리 대기 중인 광고 신청이 있습니다."),
    AD_APPLICATION_INVALID_STATUS(HttpStatus.CONFLICT,      "SHOP_015", "현재 상태에서는 처리할 수 없는 광고 신청입니다."),
    SHOP_IMAGE_FILE_EMPTY(HttpStatus.BAD_REQUEST,           "SHOP_016", "업로드할 파일이 비어 있습니다."),
    SHOP_IMAGE_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST,       "SHOP_017", "파일 크기가 최대 허용 용량(5MB)을 초과합니다."),
    SHOP_IMAGE_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST,     "SHOP_018", "지원하지 않는 이미지 형식입니다. (허용: JPEG, PNG, WebP)"),

    // ===== CHAT (담당: 임하은) =====
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND,               "CHAT_001", "존재하지 않는 채팅방입니다."),
    CHAT_ROOM_FULL(HttpStatus.CONFLICT,                     "CHAT_002", "채팅방 정원이 가득 찼습니다."),
    ALREADY_JOINED_CHAT(HttpStatus.CONFLICT,                "CHAT_003", "이미 참여 중인 채팅방입니다."),
    ALREADY_APPLIED_CHAT(HttpStatus.CONFLICT,               "CHAT_004", "이미 참여 신청한 채팅방입니다."),
    CHAT_NOT_JOINED(HttpStatus.FORBIDDEN,                   "CHAT_005", "채팅방에 참여하지 않은 사용자입니다."),
    CHAT_SETTING_FORBIDDEN(HttpStatus.FORBIDDEN,            "CHAT_006", "채팅방 개설자만 설정을 변경할 수 있습니다."),
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST,                "CHAT_007", "메시지는 최대 1000자까지 입력 가능합니다."),
    CHAT_TITLE_TOO_LONG(HttpStatus.BAD_REQUEST,             "CHAT_008", "채팅방 제목은 최대 50자까지 입력 가능합니다."),
    INVALID_CHAT_CAPACITY(HttpStatus.BAD_REQUEST,           "CHAT_009", "최대 인원은 2명 이상 50명 이하여야 합니다."),
    CHAT_MEMBER_KICKED_REJOIN(HttpStatus.FORBIDDEN,         "CHAT_010", "강퇴된 채팅방에는 재참여할 수 없습니다."),
    CHAT_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND,        "CHAT_011", "존재하지 않는 참여 신청입니다."),
    CHAT_APPLICATION_ALREADY_PROCESSED(HttpStatus.CONFLICT, "CHAT_012", "이미 처리된 참여 신청입니다."),
    CHAT_ROOM_CLOSED(HttpStatus.CONFLICT,                   "CHAT_013", "이미 종료된 채팅방입니다."),
    CHAT_ROOM_DUPLICATE(HttpStatus.CONFLICT,                "CHAT_014", "해당 게시글에 이미 개설된 채팅방이 있습니다."),
    CHAT_OWNER_CANNOT_LEAVE(HttpStatus.FORBIDDEN,           "CHAT_015", "채팅방 개설자는 직접 퇴장할 수 없습니다."),
    CHAT_MEMBER_ALREADY_INACTIVE(HttpStatus.CONFLICT,       "CHAT_016", "이미 퇴장하거나 강퇴된 멤버입니다."),
    CHAT_OWNER_CANNOT_BE_KICKED(HttpStatus.FORBIDDEN,       "CHAT_017", "채팅방 개설자는 강퇴할 수 없습니다."),
    CHAT_NOT_APPLICANT(HttpStatus.FORBIDDEN,                "CHAT_018", "본인의 참여 신청만 취소할 수 있습니다."),

    // ===== NOTIFICATION (담당: 임하은) =====
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND,            "NOTIFICATION_001", "존재하지 않는 알림입니다."),

    // ===== REPORT (담당: 박경화) =====
    // REPORT_001~004: 신고 접수 유효성 검증 (KAN-90)
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND,            "REPORT_001", "신고 대상을 찾을 수 없습니다."),
    DUPLICATE_REPORT(HttpStatus.CONFLICT,                    "REPORT_002", "이미 신고한 대상입니다."),
    REPORT_SELF(HttpStatus.BAD_REQUEST,                      "REPORT_003", "자기 자신을 신고할 수 없습니다."),
    REPORT_TARGET_INACTIVE(HttpStatus.BAD_REQUEST,           "REPORT_004", "신고할 수 없는 대상입니다."),
    // REPORT_005~007: 관리자 신고 처리 (KAN-91/92)
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND,                   "REPORT_005", "존재하지 않는 신고 내역입니다."),
    REPORT_ALREADY_RESOLVED(HttpStatus.CONFLICT,             "REPORT_006", "이미 처리된 신고 내역입니다."),
    // REPORT_007: targetType 불일치 엔드포인트 — MERCHANT 신고에 /resolve, 콘텐츠 신고에 /resolve/merchant 사용 시
    REPORT_WRONG_ENDPOINT(HttpStatus.BAD_REQUEST,            "REPORT_007", "해당 신고 유형에 맞지 않는 처리 엔드포인트입니다."),

    // ===== CS / FAQ (담당: 임하은) =====
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND,                     "FAQ_001", "존재하지 않는 FAQ입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
