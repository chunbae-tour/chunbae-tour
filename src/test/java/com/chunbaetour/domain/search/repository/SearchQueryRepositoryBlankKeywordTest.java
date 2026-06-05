package com.chunbaetour.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// SearchQueryRepositoryTraditionalMarketTest에 이미 blank 케이스 있으나
// 5개 메서드 회귀를 한 곳에서 확인하기 위해 traditional-market도 포함

/**
 * blank 키워드 시 ORDER BY 0 → MySQL ordinal 오류 회귀 방지 (KAN-242).
 * exactMatchScore()가 blank 시 Expressions.asNumber(0) 반환 → ORDER BY 0 DESC → 500.
 */
@SpringBootTest
class SearchQueryRepositoryBlankKeywordTest extends AbstractIntegrationTest {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Test
    @DisplayName("관광지 검색: 빈 키워드 → SQL 오류 없이 빈 목록 반환")
    void searchPlaces_emptyKeyword_returnEmpty() {
        assertThatCode(() -> searchQueryRepository.searchPlaces("")).doesNotThrowAnyException();
        assertThat(searchQueryRepository.searchPlaces("")).isEmpty();
    }

    @Test
    @DisplayName("가게 검색: 빈 키워드 → SQL 오류 없이 빈 목록 반환")
    void searchShops_emptyKeyword_returnEmpty() {
        assertThatCode(() -> searchQueryRepository.searchShops("")).doesNotThrowAnyException();
        assertThat(searchQueryRepository.searchShops("")).isEmpty();
    }

    @Test
    @DisplayName("메뉴 검색: 빈 키워드 → SQL 오류 없이 빈 목록 반환")
    void searchMenus_emptyKeyword_returnEmpty() {
        assertThatCode(() -> searchQueryRepository.searchMenus("")).doesNotThrowAnyException();
        assertThat(searchQueryRepository.searchMenus("")).isEmpty();
    }

    @Test
    @DisplayName("축제 검색: 빈 키워드 → SQL 오류 없이 빈 목록 반환")
    void searchFestivals_emptyKeyword_returnEmpty() {
        assertThatCode(() -> searchQueryRepository.searchFestivals("")).doesNotThrowAnyException();
        assertThat(searchQueryRepository.searchFestivals("")).isEmpty();
    }

    @Test
    @DisplayName("전통시장 검색: 빈 키워드 → SQL 오류 없이 빈 목록 반환")
    void searchTraditionalMarkets_emptyKeyword_returnEmpty() {
        assertThatCode(() -> searchQueryRepository.searchTraditionalMarkets("")).doesNotThrowAnyException();
        assertThat(searchQueryRepository.searchTraditionalMarkets("")).isEmpty();
    }
}
