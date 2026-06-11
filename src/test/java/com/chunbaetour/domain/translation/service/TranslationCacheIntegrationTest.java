package com.chunbaetour.domain.translation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.translation.client.GoogleTranslationClient;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.repository.TranslationCacheRepository;
import com.chunbaetour.domain.translation.type.LanguageCode;
import com.chunbaetour.domain.translation.type.TranslationSourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class TranslationCacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TranslationService translationService;
    @Autowired private TranslationCacheRepository translationCacheRepository;
    @Autowired private CacheManager cacheManager;

    @MockitoBean private GoogleTranslationClient googleTranslationClient;

    @AfterEach
    void cleanUp() {
        translationCacheRepository.deleteAll();
        Cache cache = cacheManager.getCache("translation");
        if (cache != null) {
            cache.clear();
        }
    }

    // 정적 도메인(FAQ) — 동일 텍스트 2번 요청 시 2번째는 Redis 캐시 hit, Google API 1번만 호출, DB에 1행만 존재
    @Test
    void translate_staticSourceType_secondRequest_hitsCacheWithoutSecondApiCall() {
        given(googleTranslationClient.translate("운영시간이 어떻게 되나요?", LanguageCode.EN))
                .willReturn("What are the business hours?");

        TranslationResponse first =
                translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ);
        assertThat(first.translatedContent()).isEqualTo("What are the business hours?");
        assertThat(translationCacheRepository.findAll()).hasSize(1);

        // DB를 비워도 Redis 캐시로 응답해야 진짜 Redis 히트 검증됨 (DB read-through와 구분)
        translationCacheRepository.deleteAll();

        TranslationResponse second =
                translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ);

        assertThat(second.translatedContent()).isEqualTo("What are the business hours?");
        verify(googleTranslationClient, times(1)).translate("운영시간이 어떻게 되나요?", LanguageCode.EN);
        assertThat(translationCacheRepository.findAll()).isEmpty();
    }

    // 동적 도메인(CHAT) — 동일 텍스트 2번 요청해도 매번 Google API 호출, DB 저장 없음
    @Test
    void translate_dynamicSourceType_alwaysCallsApiWithoutCaching() {
        given(googleTranslationClient.translate("안녕", LanguageCode.EN)).willReturn("Hello");

        translationService.translate("안녕", LanguageCode.EN, TranslationSourceType.CHAT);
        translationService.translate("안녕", LanguageCode.EN, TranslationSourceType.CHAT);

        verify(googleTranslationClient, times(2)).translate("안녕", LanguageCode.EN);
        assertThat(translationCacheRepository.findAll()).isEmpty();
    }
}
