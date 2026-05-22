package com.chunbaetour.domain.festival.entity;

import com.chunbaetour.domain.festival.type.FestivalStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import com.chunbaetour.domain.common.converter.StringListConverter;
import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;

@Entity
@Table(name = "festivals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Festival extends BaseEntity {

    private static final BigDecimal LAT_MIN = new BigDecimal("-90");
    private static final BigDecimal LAT_MAX = new BigDecimal("90");
    private static final BigDecimal LNG_MIN = new BigDecimal("-180");
    private static final BigDecimal LNG_MAX = new BigDecimal("180");
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String region;

    @Column(nullable = false, length = 255)
    private String location;

    // 위도는 -90 ~ 90이므로 정수부 최대 3자리 (부호 포함) -> precision 10, scale 7
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    // 경도는 -180 ~ 180이므로 정수부 최대 4자리 (부호 포함) -> precision 11, scale 7
    @Column(nullable = false, precision = 11, scale = 7)
    private BigDecimal lng;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "thumbnail_url", length = 512)
    private String thumbnailUrl;

    @Convert(converter = StringListConverter.class)
    @Column(name = "image_urls", columnDefinition = "JSON")
    private List<String> imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FestivalStatus status = FestivalStatus.ACTIVE;

    @Builder
    private Festival(String name, String description, String region, String location,
                     BigDecimal lat, BigDecimal lng, LocalDate startDate, LocalDate endDate,
                     String thumbnailUrl, List<String> imageUrls) {
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (region == null || region.isBlank() || region.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (location == null || location.isBlank() || location.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (lat == null || lat.compareTo(LAT_MIN) < 0 || lat.compareTo(LAT_MAX) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (lng == null || lng.compareTo(LNG_MIN) < 0 || lng.compareTo(LNG_MAX) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (thumbnailUrl != null && !thumbnailUrl.isBlank() && !URL_PATTERN.matcher(thumbnailUrl).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        this.name = name;
        this.description = description;
        this.region = region;
        this.location = location;
        this.lat = lat;
        this.lng = lng;
        this.startDate = startDate;
        this.endDate = endDate;
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrls = imageUrls;
    }
}
