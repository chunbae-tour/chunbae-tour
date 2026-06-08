package com.chunbaetour.domain.place.client;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 한국관광공사 KorService2 관광지 상세 수집 클라이언트 (KAN-221 Tier-2, 온디맨드).
 *
 * <p>관광지 상세 최초 조회 시 1회 호출되어 detailCommon2(overview)·detailIntro2(usetime/restdate)를 합쳐 반환한다.
 * 채워진 값은 Place에 영구 저장되므로 이후 조회는 외부 API를 다시 호출하지 않는다(일일 트래픽 1000/일 한도 보호).
 *
 * <p>overview(detailCommon2)는 핵심 값이라 실패 시 예외를 전파(호출부가 이번 조회 enrich를 건너뜀).
 * usetime/restdate(detailIntro2)는 보조 값이라 실패해도 overview만으로 진행한다.
 */
@Slf4j
@Component
public class TourApiPlaceDetailClient {

    private static final int CONTENT_TYPE_TOURIST_SPOT = 12;
    private static final String MOBILE_OS  = "ETC";
    private static final String MOBILE_APP = "chunbae";
    private static final String SUCCESS_CODE = "0000";

    private final RestClient restClient;
    private final String serviceKey;
    private final String baseUrl;

    public TourApiPlaceDetailClient(
            @Qualifier("tourApiRestClient") RestClient restClient,
            @Value("${tour-api.kor-service.service-key}") String serviceKey,
            @Value("${tour-api.kor-service.base-url}") String baseUrl) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
    }

    /** contentId의 상세를 수집해 반환. overview 수집 실패 시 BusinessException 전파. */
    public TourApiPlaceDetail fetchDetail(String contentId) {
        String overview = fetchOverview(contentId);
        IntroFields intro = fetchIntro(contentId); // 보조 — 실패해도 null 반환
        return new TourApiPlaceDetail(overview, intro.usetime(), intro.restdate());
    }

    private String fetchOverview(String contentId) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/detailCommon2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .encode()
                .build()
                .toUriString();
        try {
            TourApiDetailCommonResponse resp = restClient.get().uri(uri).retrieve()
                    .body(TourApiDetailCommonResponse.class);
            if (resp == null || !SUCCESS_CODE.equals(resp.resultCode())) {
                // 실패 원인 구분: 응답 자체 없음 / 헤더(resultCode) 누락 / 코드는 왔으나 성공코드 아님
                String diag = (resp == null) ? "응답 없음(resp=null)"
                        : (resp.resultCode() == null) ? "헤더/resultCode 누락(null)"
                        : "resultCode=" + resp.resultCode();
                log.error("detailCommon2 비정상 응답: contentId={}, 원인={}", contentId, diag);
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return blankToNull(resp.overview());
        } catch (RestClientResponseException e) {
            log.error("detailCommon2 HTTP error: contentId={}, status={}", contentId, e.getStatusCode());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        } catch (RestClientException e) {
            log.error("detailCommon2 network error: contentId={}", contentId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private IntroFields fetchIntro(String contentId) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/detailIntro2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", CONTENT_TYPE_TOURIST_SPOT)
                .encode()
                .build()
                .toUriString();
        try {
            TourApiDetailIntroResponse resp = restClient.get().uri(uri).retrieve()
                    .body(TourApiDetailIntroResponse.class);
            if (resp == null || !SUCCESS_CODE.equals(resp.resultCode())) {
                log.warn("detailIntro2 비정상 응답(overview만 사용): contentId={}", contentId);
                return IntroFields.EMPTY;
            }
            return new IntroFields(blankToNull(resp.usetime()), blankToNull(resp.restdate()));
        } catch (RestClientException e) {
            log.warn("detailIntro2 실패(overview만 사용): contentId={}, err={}", contentId, e.getMessage());
            return IntroFields.EMPTY;
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private record IntroFields(String usetime, String restdate) {
        static final IntroFields EMPTY = new IntroFields(null, null);
    }
}
