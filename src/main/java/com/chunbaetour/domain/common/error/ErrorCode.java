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
    INVALID_CURSOR_PAIR(HttpStatus.BAD_REQUEST,              "COMMON_011", "커서 페이징 쌍(cursor, cursorRating 등)이 올바르게 전달되지 않았습니다."),
    // COMMON_012: multipart 파싱 단계 파일 크기 초과 — 도메인 무관 공용 (GlobalExceptionHandler.handleMaxUploadSize)
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST,                   "COMMON_012", "파일 크기가 최대 허용 용량을 초과합니다."),

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
    // AUTH_024: 도메인별 제재(POST/COMMENT/REVIEW 쓰기 차단) — 계정 전체 정지(AUTH_012)와 구분 (SanctionCheckInterceptor).
    DOMAIN_SANCTIONED(HttpStatus.FORBIDDEN, "AUTH_024", "해당 도메인에서 활동이 제한된 계정입니다."),
    // AUTH_013: 로그아웃 시 Access Token이 블랙리스트에 등록되며, 이후 같은 토큰으로 요청하면 거부된다.
    //           남은 만료 시간 동안만 블랙리스트에 머무르므로 자연 만료 후에는 같은 tokenId가 새로 발급될 가능성도 사라진다 (UUID).
    BLACKLISTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_013", "로그아웃된 토큰입니다."),
    // AUTH_014: IP 기반 rate limit 초과 (sa-docs/11 운영 보안 정책 §Rate Limit).
    //           회원가입 3회/10분/IP, 로그인 5회/분/IP 등 endpoint별 정책 초과 시 응답.
    //           응답에 Retry-After 헤더 + X-RateLimit-Limit/Remaining 헤더 첨부 (REST 표준).
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_014", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_015", "존재하지 않는 사용자입니다."),
    // AUTH_016: 탈퇴(soft-delete)된 계정에 운영자 정지/해제를 시도한 경우. 운영자 입력 오류이므로 400.
    USER_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "AUTH_016", "탈퇴한 계정입니다."),
    // ===== 소셜 로그인 (카카오/네이버) =====
    // AUTH_017: 가입 시 전화번호 중복 — 중복가입 방지 기준(전화번호 UNIQUE).
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "AUTH_017", "이미 사용 중인 전화번호입니다."),
    // AUTH_018: 지원하지 않는 소셜 공급자(path가 kakao/naver 외).
    OAUTH_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "AUTH_018", "지원하지 않는 소셜 로그인입니다."),
    // AUTH_019: 공급자 토큰 교환/사용자 조회 실패(키 미설정·코드 만료·외부 장애 등).
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AUTH_019", "소셜 로그인 처리 중 오류가 발생했습니다."),
    // AUTH_020: 가입 티켓 서명 오류/만료/타입 불일치.
    OAUTH_SIGNUP_TICKET_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_020", "소셜 가입 정보가 유효하지 않습니다. 다시 시도해 주세요."),
    // AUTH_021: 이미 가입된 소셜 계정으로 신규 가입 시도(동시 가입 등).
    OAUTH_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_021", "이미 가입된 소셜 계정입니다."),
    // AUTH_022: 공급자가 이메일을 제공하지 않아(예: 카카오 이메일 동의 안 함) 소셜 가입 불가. 클라이언트 입력
    // 이메일을 신뢰하지 않고 공급자 검증 이메일만 쓰므로(선점 방지), 이메일 동의가 없으면 가입을 진행하지 않는다.
    OAUTH_EMAIL_NOT_PROVIDED(HttpStatus.BAD_REQUEST, "AUTH_022", "소셜 계정에서 이메일을 제공받지 못했습니다. 이메일 제공에 동의해 주세요."),
    // AUTH_023: 인가코드 만료/재사용/무효 등 사용자 재로그인으로 해소 가능한 토큰 교환 실패(400).
    // redirect_uri 불일치, 앱키/시크릿 오류 등 서버 설정 문제는 OAUTH_PROVIDER_ERROR(502)로 분리 —
    // 판별 기준은 OauthErrorClassifier(카카오 error_code=KOE320 / 네이버 error=invalid_grant만 400).
    OAUTH_INVALID_AUTHORIZATION(HttpStatus.BAD_REQUEST, "AUTH_023", "소셜 인가 정보가 유효하지 않습니다. 다시 시도해 주세요."),

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
    MARKET_SYNC_IN_PROGRESS(HttpStatus.CONFLICT,            "PLACE_003", "전통시장 데이터 수집이 진행 중입니다. 잠시 후 다시 시도해주세요."),
    FESTIVAL_NOT_FOUND(HttpStatus.NOT_FOUND,                "FESTIVAL_001", "존재하지 않는 축제입니다."),
    FESTIVAL_DELETED(HttpStatus.FORBIDDEN,                  "FESTIVAL_002", "삭제된 축제입니다."),
    FESTIVAL_FORBIDDEN(HttpStatus.FORBIDDEN,                "FESTIVAL_003", "축제 관리 권한이 없습니다."),
    FESTIVAL_FETCH_IN_PROGRESS(HttpStatus.CONFLICT,         "FESTIVAL_004", "이미 수집이 진행 중입니다. 잠시 후 다시 시도해주세요."),
    INVALID_LOCATION(HttpStatus.BAD_REQUEST,                "PLACE_004", "위치 정보가 올바르지 않습니다."),
    SEARCH_KEYWORD_TOO_SHORT(HttpStatus.BAD_REQUEST,        "PLACE_005", "검색어는 최소 1자 이상 입력해주세요."),
    SEARCH_KEYWORD_TOO_LONG(HttpStatus.BAD_REQUEST,         "PLACE_006", "검색어는 최대 50자까지 입력 가능합니다."),
    MAP_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PLACE_007", "길찾기 서비스를 일시적으로 사용할 수 없습니다."),
    INVALID_SEARCH_RADIUS(HttpStatus.BAD_REQUEST,           "PLACE_008", "유효하지 않은 반경 범위입니다. (최대 20km)"),
    SEARCH_INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST,       "PLACE_009", "검색 시작일은 종료일보다 늦을 수 없습니다."),
    LIKE_ALREADY_EXISTS(HttpStatus.CONFLICT,                "PLACE_010", "이미 찜한 대상입니다."),
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND,                  "PLACE_011", "찜하지 않은 대상입니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT,            "PLACE_012", "이미 리뷰를 작성한 관광지입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND,                "PLACE_013", "존재하지 않는 리뷰입니다."),
    REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN,                "PLACE_014", "본인의 리뷰만 수정·삭제할 수 있습니다."),
    // PLACE_015: 이미 soft delete(DELETED)된 관광지를 다시 삭제 시도 (운영자 멱등 가드, S07 리뷰 I)
    PLACE_ALREADY_DELETED(HttpStatus.CONFLICT,            "PLACE_015", "이미 삭제된 관광지입니다."),
    GEOCODING_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND,      "PLACE_016", "API를 통한 위치/주소 변환 결과를 찾을 수 없습니다."),
    // PLACE_017: 관광지 외부 API(KorService2) 수집이 이미 진행 중 (KAN-221 다중 인스턴스 가드)
    PLACE_SYNC_IN_PROGRESS(HttpStatus.CONFLICT,          "PLACE_017", "관광지 데이터 수집이 진행 중입니다. 잠시 후 다시 시도해주세요."),

    // ===== BANNER (담당: 정민교, Admin Epic KAN-177 S09) =====
    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND,                 "BANNER_001", "존재하지 않는 배너입니다."),
    // BANNER_002: 이미 soft delete(DELETED)된 배너를 다시 수정/삭제 시도 (운영자 멱등 가드, S07 정책 미러)
    BANNER_ALREADY_DELETED(HttpStatus.CONFLICT,            "BANNER_002", "이미 삭제된 배너입니다."),

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
    PAYMENT_ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT,     "PAY_021", "대기 중인 결제만 취소할 수 있습니다."),
    DUPLICATE_QR_PAY_REQUEST(HttpStatus.CONFLICT,          "PAY_022", "이미 대기 중인 QR 결제 요청이 있습니다."),
    SELF_PAYMENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST,       "PAY_023", "본인 가게에는 결제할 수 없습니다."),
    ZERO_AMOUNT_NOT_ALLOWED(HttpStatus.BAD_REQUEST,        "PAY_024", "결제 금액은 0원보다 커야 합니다."),
    QR_PAY_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT,  "PAY_025", "현재 상태에서는 해당 작업을 수행할 수 없습니다."),
    QR_PAY_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND,         "PAY_026", "존재하지 않는 QR 결제 요청입니다."),
    SHOP_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND,            "PAY_027", "상인 엽전 지갑을 찾을 수 없습니다."),
    QR_PAY_CONFIRM_FORBIDDEN(HttpStatus.FORBIDDEN,         "PAY_028", "본인 가게의 결제 요청만 승인/거절할 수 있습니다."),
    // PAY_029: QR payload의 nonce가 현재 가게 nonce와 불일치 — 재발급으로 무효화된 옛 QR (KAN-253)
    QR_PAY_NONCE_MISMATCH(HttpStatus.CONFLICT,             "PAY_029", "만료된 QR 코드입니다. 최신 QR로 다시 시도해주세요."),
    // PAY_030: 하루 충전 누적액(진행중+완료 이력)이 일일 한도를 초과 (KAN-293)
    // 메시지에 한도 금액을 박지 않는다 — 한도는 DailyChargeLimiter.DAILY_LIMIT 단일 소스에서만 관리
    DAILY_CHARGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST,    "PAY_030", "1일 충전 한도를 초과했습니다."),

    // ===== STORE (담당: 신현민) =====
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND,                 "STORE_001", "존재하지 않는 상품입니다."),
    PRODUCT_SOLD_OUT(HttpStatus.CONFLICT,                   "STORE_002", "품절된 상품입니다."),
    INVALID_PURCHASE_QUANTITY(HttpStatus.BAD_REQUEST,       "STORE_003", "구매 수량은 1개 이상이어야 합니다."),
    PURCHASE_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST,      "STORE_004", "1회 최대 구매 수량을 초과했습니다."),
    PURCHASE_PROCESSING(HttpStatus.SERVICE_UNAVAILABLE,     "STORE_005", "구매 처리 중입니다. 잠시 후 다시 시도해주세요."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND,                   "STORE_006", "존재하지 않는 주문입니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST,         "STORE_007", "이미 취소된 주문입니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND,                    "STORE_008", "보유 아이템을 찾을 수 없습니다."),
    ITEM_FORBIDDEN(HttpStatus.FORBIDDEN,                    "STORE_009", "본인 보유 아이템만 사용할 수 있습니다."),
    ITEM_ALREADY_USED(HttpStatus.CONFLICT,                  "STORE_010", "이미 사용된 아이템입니다."),
    ITEM_EXPIRED(HttpStatus.CONFLICT,                       "STORE_011", "만료된 아이템입니다."),
    ITEM_QR_EXPIRED(HttpStatus.UNAUTHORIZED,                "STORE_012", "아이템 QR이 만료되었습니다."),
    ITEM_QR_INVALID(HttpStatus.UNAUTHORIZED,                "STORE_013", "아이템 QR이 유효하지 않습니다."),

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
    SHOP_WALLET_ALREADY_EXISTS(HttpStatus.CONFLICT,          "SHOP_016", "이미 등록된 가게 수익 지갑이 있습니다."),
    SHOP_IMAGE_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST,       "SHOP_017", "파일 크기가 최대 허용 용량(5MB)을 초과합니다."),
    SHOP_IMAGE_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST,     "SHOP_018", "지원하지 않는 이미지 형식입니다. (허용: JPEG, PNG, WebP)"),
    // SHOP_019~021: 상인 인증 admin (KAN-204, Admin Epic KAN-177 S05)
    // 취소 가드는 cert 상태(APPROVED) 기반 SHOP_CERTIFICATION_INVALID_STATUS로 통일.
    // SHOP_021은 구 SHOP_NOT_CERTIFIED(취소 가드)로 쓰였다가 제거된 뒤, "가게당 APPROVED 인증 ≤1" 불변식 위반용으로 재사용한다.
    SHOP_CERTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND,      "SHOP_019", "존재하지 않는 인증 신청입니다."),
    SHOP_CERTIFICATION_INVALID_STATUS(HttpStatus.CONFLICT,  "SHOP_020", "현재 상태에서는 처리할 수 없는 인증 신청입니다."),
    SHOP_ALREADY_CERTIFIED(HttpStatus.CONFLICT,             "SHOP_021", "이미 인증된 가게입니다. 가게당 유효 인증은 1건만 허용됩니다."),
    SHOP_NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND,             "SHOP_022", "존재하지 않는 가게 공지입니다."),
    SHOP_STATUS_FORBIDDEN(HttpStatus.FORBIDDEN,             "SHOP_023", "상인이 변경할 수 없는 상태입니다."),
    SHOP_IMAGE_FILE_EMPTY(HttpStatus.BAD_REQUEST,           "SHOP_024", "업로드할 파일이 비어 있습니다."),

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
    CHAT_OWNER_CANNOT_LEAVE(HttpStatus.FORBIDDEN,           "CHAT_015", "채팅방 개설자는 위임 후 퇴장할 수 있습니다."),
    CHAT_MEMBER_ALREADY_INACTIVE(HttpStatus.CONFLICT,       "CHAT_016", "이미 퇴장하거나 강퇴된 멤버입니다."),
    CHAT_OWNER_CANNOT_BE_KICKED(HttpStatus.FORBIDDEN,       "CHAT_017", "채팅방 개설자는 강퇴할 수 없습니다."),
    CHAT_NOT_APPLICANT(HttpStatus.FORBIDDEN,                "CHAT_018", "본인의 참여 신청만 취소할 수 있습니다."),
    CHAT_OWNER_TRANSFER_INVALID_TARGET(HttpStatus.BAD_REQUEST, "CHAT_019", "방장 위임 대상은 본인이 아닌 활성 참여자여야 합니다."),
    CHAT_FILE_EMPTY(HttpStatus.BAD_REQUEST,                 "CHAT_020", "업로드할 파일이 비어 있습니다."),
    CHAT_FILE_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST,      "CHAT_021", "지원하지 않는 파일 형식입니다. (이미지: JPEG/PNG/WebP, 문서: PDF/DOCX/XLSX/PPTX/HWP)"),
    CHAT_IMAGE_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST,       "CHAT_022", "이미지 크기가 최대 허용 용량(5MB)을 초과합니다."),
    CHAT_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST,             "CHAT_023", "파일 크기가 최대 허용 용량(10MB)을 초과합니다."),
    CHAT_FILE_OWNERSHIP_INVALID(HttpStatus.FORBIDDEN,       "CHAT_024", "해당 채팅방에 업로드되지 않은 파일입니다."),

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
    // REPORT_008: 이미 정지된 계정 재정지 시도
    REPORT_TARGET_ALREADY_SUSPENDED(HttpStatus.CONFLICT,     "REPORT_008", "이미 정지된 계정입니다."),
    // REPORT_009: 제재 이력 없음 — 운영자 조기 해제 시 존재하지 않는 sanctionId
    SANCTION_NOT_FOUND(HttpStatus.NOT_FOUND,                 "REPORT_009", "존재하지 않는 제재 이력입니다."),
    // REPORT_010: 허용되지 않는 신고 상태 전이 — 오판 정정은 RESOLVED→DISMISSED만 허용
    REPORT_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "REPORT_010", "허용되지 않는 신고 상태 변경입니다."),

    // ===== CS / FAQ (담당: 임하은) =====
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND,                     "FAQ_001", "존재하지 않는 FAQ입니다."),

    // ===== CS / SUPPORT (담당: 임하은) =====
    SUPPORT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND,            "CS_001", "존재하지 않는 상담방입니다."),
    SUPPORT_ROOM_ALREADY_CLOSED(HttpStatus.CONFLICT,        "CS_002", "이미 종료된 상담방입니다."),
    // CS_003: USER 본인 상담방 또는 ADMIN 전용 — 타인 접근 차단
    SUPPORT_ROOM_FORBIDDEN(HttpStatus.FORBIDDEN,            "CS_003", "해당 상담방에 접근할 권한이 없습니다."),
    // CS_004: WAITING 상태 상담방 이미 존재 — 중복 생성 차단
    SUPPORT_ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT,        "CS_004", "이미 진행 중인 상담방이 있습니다."),
    // CS_005: 이미 배정된 상담방 — 중복 배정 차단
    SUPPORT_ROOM_ALREADY_ASSIGNED(HttpStatus.CONFLICT,      "CS_005", "이미 배정된 상담방입니다."),
    // CS_006~010: 상담 파일/이미지 업로드 검증 (KAN-310) — CHAT_020~024 대응
    SUPPORT_FILE_EMPTY(HttpStatus.BAD_REQUEST,              "CS_006", "업로드할 파일이 비어 있습니다."),
    SUPPORT_FILE_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST,   "CS_007", "지원하지 않는 파일 형식입니다. (이미지: JPEG/PNG/WebP, 문서: PDF/DOCX/XLSX/PPTX/HWP)"),
    SUPPORT_IMAGE_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST,    "CS_008", "이미지 크기가 최대 허용 용량(5MB)을 초과합니다."),
    SUPPORT_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST,          "CS_009", "파일 크기가 최대 허용 용량(10MB)을 초과합니다."),
    SUPPORT_FILE_OWNERSHIP_INVALID(HttpStatus.FORBIDDEN,    "CS_010", "해당 상담방에 업로드되지 않은 파일입니다."),

    // ===== COMPANION / COMPANION REVIEW (담당: 임하은, CR 프리픽스 공유) =====
    COMPANION_REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT,    "CR_001", "이미 작성한 동행 리뷰입니다."),
    COMPANION_REVIEW_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CR_002", "자기 자신에게 리뷰를 작성할 수 없습니다."),
    COMPANION_REVIEW_NOT_MEMBER(HttpStatus.FORBIDDEN,       "CR_003", "동행 참여자가 아니면 리뷰를 작성할 수 없습니다."),
    // CR_004~007: 동행(Companion) 시작/종료/참여자 관리 — 고도화 #5·#6에서 사용
    // 동행 시작 시 같은 방에 기존 동행 존재 → status로 분기: ENDED는 CR_004(재시작 불가), ONGOING은 CR_007(이미 진행 중)
    COMPANION_ALREADY_EXISTS(HttpStatus.CONFLICT,           "CR_004", "동행을 재시작할 수 없습니다."),
    COMPANION_NOT_FOUND(HttpStatus.NOT_FOUND,               "CR_005", "존재하지 않는 동행입니다."),
    COMPANION_ALREADY_ENDED(HttpStatus.CONFLICT,            "CR_006", "이미 종료된 동행입니다."),
    COMPANION_ALREADY_STARTED(HttpStatus.CONFLICT,          "CR_007", "이미 진행 중인 동행이 있습니다."),
    COMPANION_PARTICIPANT_ALREADY_EXISTS(HttpStatus.CONFLICT, "CR_008", "이미 동행에 참여 중인 멤버입니다."),
    // 동행 ENDED 전에는 리뷰 작성 불가 — 고도화 #25
    COMPANION_NOT_ENDED(HttpStatus.CONFLICT,                "CR_009", "동행 종료 후에만 리뷰를 작성할 수 있습니다."),
    // 동행 생성/참여자 추가 시 기간 겹침 검증 — 고도화 #1
    COMPANION_DATE_OVERLAP(HttpStatus.CONFLICT,             "CR_010", "겹치는 기간에 진행 중인 동행이 있습니다."),
    // CR_011~015: 동행 생애주기 자동화 + 참여자별 종료/리뷰 자격 — 고도화 #2
    // 리뷰 작성 시 reviewer/target 둘 다 endParticipation을 마쳐야 함
    COMPANION_REVIEW_PARTICIPANT_NOT_ENDED(HttpStatus.CONFLICT, "CR_011", "양쪽 모두 동행 참여를 종료해야 리뷰를 작성할 수 있습니다."),
    // 리뷰 작성 시 reviewer/target 둘 중 한쪽이라도 정지 계정이면 차단
    COMPANION_REVIEW_SUSPENDED_ACCOUNT(HttpStatus.FORBIDDEN, "CR_012", "정지된 계정과는 리뷰를 작성할 수 없습니다."),
    // endParticipation 호출자가 해당 동행의 참여자가 아님
    COMPANION_PARTICIPANT_NOT_FOUND(HttpStatus.FORBIDDEN,   "CR_013", "동행 참여자가 아닙니다."),
    // endParticipation은 Companion.status==ENDED(여행 종료, 날짜 기반 배치job)일 때만 호출 가능
    COMPANION_NOT_ENDED_FOR_PARTICIPATION(HttpStatus.CONFLICT, "CR_014", "여행이 종료되지 않아 참여를 종료할 수 없습니다."),
    // endParticipation 중복 호출 — 이미 endedAt 세팅됨
    COMPANION_PARTICIPATION_ALREADY_ENDED(HttpStatus.CONFLICT, "CR_015", "이미 참여 종료 처리되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
