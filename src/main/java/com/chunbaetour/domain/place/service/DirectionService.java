package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.Coord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectionService {

    private final KakaoLocalApiClient kakaoClient;
    private final ExecutorService kakaoVirtualThreadExecutor;

    @Value("${kakao.map.map-url}")
    private String mapUrl;

    public String buildKakaoMapUrl(Coord origin, Coord dest) {
        // [MEDIUM -> CRITICAL 수정] 기본 ForkJoinPool 대신 Virtual Thread Executor 사용
        CompletableFuture<String> originFuture = CompletableFuture.supplyAsync(() -> kakaoClient.getAddressName(origin, "출발지"), kakaoVirtualThreadExecutor);
        CompletableFuture<String> destFuture = CompletableFuture.supplyAsync(() -> kakaoClient.getAddressName(dest, "도착지"), kakaoVirtualThreadExecutor);

        try {
            // [HIGH] 무한 대기 방지를 위한 전체 5초 타임아웃
            CompletableFuture.allOf(originFuture, destFuture).get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            originFuture.cancel(true);
            destFuture.cancel(true);
            log.warn("Kakao API parallel fetching timed out");
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        }
        
        String originName = originFuture.join();
        String destName = destFuture.join();

        // [CRITICAL] 카카오맵 URL은 쉼표(,)를 구분자로 사용하므로, 이름에 쉼표가 있으면 파싱 에러 발생 -> 공백 치환
        String safeOriginName = originName.replace(",", " ");
        String safeDestName = destName.replace(",", " ");

        String encodedOrigin = URLEncoder.encode(safeOriginName, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedDest = URLEncoder.encode(safeDestName, StandardCharsets.UTF_8).replace("+", "%20");

        return String.format(
                Locale.ROOT,
                "%s/%s,%.8f,%.8f/to/%s,%.8f,%.8f",
                mapUrl,
                encodedOrigin, origin.lat().doubleValue(), origin.lng().doubleValue(),
                encodedDest, dest.lat().doubleValue(), dest.lng().doubleValue()
        );
    }
}
