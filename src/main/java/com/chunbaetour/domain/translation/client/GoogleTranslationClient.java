package com.chunbaetour.domain.translation.client;

import com.chunbaetour.domain.translation.type.LanguageCode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

// Google Cloud Translation API v2 클라이언트 — 호출 실패 시 TranslationClientException
@Slf4j
@Component
public class GoogleTranslationClient {

    private static final String TRANSLATE_URL = "https://translation.googleapis.com/language/translate/v2";

    private final RestClient restClient;
    private final String apiKey;

    public GoogleTranslationClient(
            @Qualifier("googleTranslationRestClient") RestClient restClient,
            @Value("${google.translation.api-key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    // 텍스트를 targetLanguage로 번역. 실패 시 TranslationClientException
    public String translate(String text, LanguageCode targetLanguage) {
        TranslateRequest request = new TranslateRequest(List.of(text), targetLanguage.getApiCode(), "text");
        try {
            TranslateResponse response = restClient.post()
                    .uri(UriComponentsBuilder.fromUriString(TRANSLATE_URL)
                            .queryParam("key", apiKey)
                            .build().toUri())
                    .body(request)
                    .retrieve()
                    .body(TranslateResponse.class);

            if (response == null
                    || response.data() == null
                    || response.data().translations() == null
                    || response.data().translations().isEmpty()) {
                throw new TranslationClientException("Google Translation API returned empty response");
            }
            String translatedText = response.data().translations().get(0).translatedText();
            if (translatedText == null || translatedText.isBlank()) {
                throw new TranslationClientException("Google Translation API returned invalid translated text");
            }
            return translatedText;
        } catch (RestClientException e) {
            log.error("Google Translation API 호출 실패. targetLanguage={}", targetLanguage, e);
            throw new TranslationClientException("Google Translation API 호출 실패", e);
        }
    }

    // format = "text": HTML 기본값 방지 — 채팅 메시지의 <, > 등 특수문자 오해석 차단
    private record TranslateRequest(List<String> q, String target, String format) {}

    private record TranslateResponse(TranslateData data) {
        private record TranslateData(List<Translation> translations) {
            private record Translation(String translatedText, String detectedSourceLanguage) {}
        }
    }
}
