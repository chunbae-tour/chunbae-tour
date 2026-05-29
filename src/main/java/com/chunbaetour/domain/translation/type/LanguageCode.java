package com.chunbaetour.domain.translation.type;

// Google Translation API 지원 언어 코드 — apiCode는 Google API에 전달하는 실제 언어 코드 값
public enum LanguageCode {
    KO("ko"),
    EN("en"),
    JA("ja"),
    ZH_CN("zh-CN");  // 중국어 간체. 번체 추가 시 ZH_TW("zh-TW") 추가

    private final String apiCode;

    LanguageCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiCode() {
        return apiCode;
    }
}
