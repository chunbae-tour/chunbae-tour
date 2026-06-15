package com.chunbaetour.domain.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 업로드/조회 설정 (E10).
 *
 * <p>버킷명·리전은 "비밀값"이 아니라 "환경설정값"이라 application.yml 평문(+env override)으로 둔다.
 * 자격증명은 코드/설정에 두지 않고 {@code DefaultCredentialsProvider}가 운영 ECS task-role에서 자동 해석한다.
 *
 * <p>모든 필드에 기본값을 둬서, 설정이 비어도 빈 생성(프로퍼티 바인딩)이 실패하지 않게 한다
 * (테스트/로컬 context 로드 보호 — 로컬은 S3 미사용 stub).
 */
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

    /** 업로드 버킷명. 운영 = chunbae-tour-uploads(비공개 버킷). */
    private String bucket = "chunbae-tour-uploads";

    /** S3 리전. */
    private String region = "ap-northeast-2";

    /** presigned GET URL 만료시간. 노출 최소화를 위해 짧게(기본 10분) — 이미지 로드엔 충분. */
    private Duration presignExpiry = Duration.ofMinutes(10);

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Duration getPresignExpiry() {
        return presignExpiry;
    }

    public void setPresignExpiry(Duration presignExpiry) {
        this.presignExpiry = presignExpiry;
    }
}
