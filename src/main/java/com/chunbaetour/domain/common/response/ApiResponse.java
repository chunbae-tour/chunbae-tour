package com.chunbaetour.domain.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "OK";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
