package com.chunbaetour.domain.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트/Presigner 빈 (E10 — 공통 위치, 후속 도메인 재사용 가능).
 *
 * <p>자격증명 = {@link DefaultCredentialsProvider} → 운영 ECS task-role의 자동 자격증명을 사용한다(액세스키 하드코딩 0).
 * 로컬/테스트는 자격증명이 없어도 <b>빈 생성은 성공</b>한다 — provider는 지연 해석(resolveCredentials)이라
 * 실제 S3 호출 시점에만 자격증명이 필요하다. 따라서 context 로드(테스트 포함)는 깨지지 않는다.
 *
 * <p>S3 저장소 구현({@code S3ShopImageStorage})은 운영 프로파일에서만 활성화될 예정(후속 PR) —
 * 로컬은 stub을 쓰므로 이 빈들이 로컬에서 생성돼 있어도 호출되지 않는다(무해).
 */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(S3Properties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
