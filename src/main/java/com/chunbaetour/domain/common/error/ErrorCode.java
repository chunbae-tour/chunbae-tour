package com.chunbaetour.domain.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Access 토큰이 만료되었습니다."),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_003", "Access 토큰이 유효하지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_006", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_007", "권한이 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_008", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "AUTH_009", "이미 사용 중인 닉네임입니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "AUTH_010", "비밀번호 형식이 올바르지 않습니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "AUTH_011", "이메일 형식이 올바르지 않습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "AUTH_012", "정지된 계정입니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_001", "서버 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "잘못된 요청입니다."),

    // ===== PLACE (담당: 김인목) =====
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND,                   "PLACE_001", "존재하지 않는 관광지입니다."),
    MARKET_NOT_FOUND(HttpStatus.NOT_FOUND,                  "PLACE_002", "존재하지 않는 전통시장입니다."),
    FESTIVAL_NOT_FOUND(HttpStatus.NOT_FOUND,                "PLACE_003", "존재하지 않는 축제입니다."),
    INVALID_LOCATION(HttpStatus.BAD_REQUEST,                "PLACE_004", "위치 정보가 올바르지 않습니다."),
    SEARCH_KEYWORD_TOO_SHORT(HttpStatus.BAD_REQUEST,        "PLACE_005", "검색어는 최소 1자 이상 입력해주세요."),
    SEARCH_KEYWORD_TOO_LONG(HttpStatus.BAD_REQUEST,         "PLACE_006", "검색어는 최대 50자까지 입력 가능합니다."),
    MAP_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PLACE_007", "길찾기 서비스를 일시적으로 사용할 수 없습니다."),
    INVALID_SEARCH_RADIUS(HttpStatus.BAD_REQUEST,           "PLACE_008", "유효하지 않은 반경 범위입니다. (최대 20km)");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
