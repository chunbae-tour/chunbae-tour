package com.chunbaetour.domain.common.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * S3 업로드/조회 설정 (E10).
 *
 * <p>버킷명·리전은 "비밀값"이 아니라 "환경설정값"이라 application.yml 평문(+env override)으로 둔다.
 * 자격증명은 코드/설정에 두지 않고 {@code DefaultCredentialsProvider}가 운영 ECS task-role에서 자동 해석한다.
 *
 * <p>모든 필드에 기본값을 둬서, 환경변수 <b>미설정</b> 시엔 빈 생성(바인딩)이 실패하지 않게 한다
 * (테스트/로컬 context 로드 보호 — 로컬은 S3 미사용 stub).
 *
 * <p>단 환경변수가 <b>빈 문자열로 명시</b>되면(예: ECS task-def에 {@code AWS_REGION=""}) Spring placeholder는
 * 빈 문자열을 그대로 바인딩(default로 안 빠짐) → {@code Region.of("")}가 빈 생성 시 {@code IllegalArgumentException}.
 * 이런 오설정을 부팅 시점에 명확히 잡기 위해 {@code @Validated} + {@code @NotBlank}로 선제 검증한다
 * (CodeRabbit·hyeonmin02 리뷰 반영). presignExpiry는 {@code @Positive}가 {@code Duration} 미지원이라
 * (UnexpectedTypeException 위험) setter에서 수동 검증한다.
 */
@Validated
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

    /** 업로드 버킷명. 운영 = chunbae-tour-uploads(비공개 버킷). 빈 문자열 명시 시 부팅 차단. */
    @NotBlank
    private String bucket = "chunbae-tour-uploads";

    /** S3 리전. 빈 문자열이면 Region.of("")가 예외 → @NotBlank로 부팅 시점 차단. */
    @NotBlank
    private String region = "ap-northeast-2";

    /** presigned GET URL 만료시간. 노출 최소화를 위해 짧게(기본 10분) — 이미지 로드엔 충분. 0/음수/null은 setter에서 거부. */
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
        // @Positive는 Duration 미지원(UnexpectedTypeException) → 수동 검증(hyeonmin02 리뷰).
        if (presignExpiry == null || presignExpiry.isZero() || presignExpiry.isNegative()) {
            throw new IllegalArgumentException("aws.s3.presign-expiry must be a positive Duration");
        }
        this.presignExpiry = presignExpiry;
    }
}
