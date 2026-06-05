package com.chunbaetour.domain.market.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전통시장 엔티티. 공공데이터포털 전통시장 정보 API에서 수집한 데이터를 저장.
 */
@Entity
@Table(
    name = "traditional_markets",
    indexes = {
        @Index(name = "idx_traditional_markets_name", columnList = "name"),
        @Index(name = "idx_traditional_markets_lat_lng", columnList = "lat, lng")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TraditionalMarket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 시장명 (예: "동대문종로5가시장") */
    @Column(nullable = false, length = 100)
    private String name;

    /** 도로명주소 (예: "서울특별시 중구 을지로 203") */
    @Column(nullable = false, length = 255)
    private String address;

    /** 위도 — DECIMAL(10,7): 소수점 7자리 (정밀도 약 1cm) */
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    /** 경도 — DECIMAL(11,7): 정수부 4자리 (180.0000000 수용) */
    @Column(nullable = false, precision = 11, scale = 7)
    private BigDecimal lng;

    /** 시장유형 (예: "상설장", "상설장+3일장", "4일장") */
    @Column(length = 50)
    private String marketType;

    /** 전화번호 (예: "02-123-4567") */
    @Column(length = 20)
    private String phoneNumber;

    /** 홈페이지 주소 */
    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    /** 개설년도 (예: 1950) */
    @Column(name = "establish_year")
    private Integer establishYear;

    @Builder
    private TraditionalMarket(String name, String address, BigDecimal lat, BigDecimal lng,
                              String marketType, String phoneNumber, String homepageUrl,
                              Integer establishYear) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("시장명은 필수입니다.");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
        if (lat == null || lat.compareTo(new BigDecimal("-90")) < 0 || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException("위도는 -90에서 90 사이여야 합니다.");
        }
        if (lng == null || lng.compareTo(new BigDecimal("-180")) < 0 || lng.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("경도는 -180에서 180 사이여야 합니다.");
        }

        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.marketType = marketType;
        this.phoneNumber = phoneNumber;
        this.homepageUrl = homepageUrl;
        this.establishYear = establishYear;
    }

    /** 시장 정보 업데이트 (동기화/관리자 공용). 좌표 보정 포함. */
    public void update(BigDecimal lat, BigDecimal lng, String marketType,
                       String phoneNumber, String homepageUrl, Integer establishYear) {
        if (lat != null) this.lat = lat;
        if (lng != null) this.lng = lng;
        if (marketType != null) this.marketType = marketType;
        if (phoneNumber != null) this.phoneNumber = phoneNumber;
        if (homepageUrl != null) this.homepageUrl = homepageUrl;
        if (establishYear != null) this.establishYear = establishYear;
    }
}
