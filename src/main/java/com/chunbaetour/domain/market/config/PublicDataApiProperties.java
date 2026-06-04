package com.chunbaetour.domain.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터포털 API 설정 프로퍼티.
 */
@ConfigurationProperties(prefix = "public-data")
public class PublicDataApiProperties {

    private Market market = new Market();

    public Market getMarket() {
        return market;
    }

    public void setMarket(Market market) {
        this.market = market;
    }

    public static class Market {
        private String url;
        private String key;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }
}
