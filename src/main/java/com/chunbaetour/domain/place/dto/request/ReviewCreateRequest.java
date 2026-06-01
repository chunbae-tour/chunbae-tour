package com.chunbaetour.domain.place.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 관광지 리뷰 작성 요청 DTO.
 *
 * <ul>
 *   <li>rating: 1~5점 정수 (필수)</li>
 *   <li>content: 1자 이상, 최대 500자 (필수)</li>
 *   <li>imageUrls: 최대 5개 URL 목록 (선택)</li>
 * </ul>
 */
public record ReviewCreateRequest(

    @NotNull(message = "별점은 필수입니다.")
    @Min(value = 1, message = "별점은 1점 이상이어야 합니다.")
    @Max(value = 5, message = "별점은 5점 이하이어야 합니다.")
    Integer rating,

    @NotBlank(message = "리뷰 내용은 필수입니다.")
    @Size(max = 500, message = "리뷰 내용은 최대 500자까지 입력 가능합니다.")
    String content,

    @Size(max = 5, message = "이미지는 최대 5개까지 첨부 가능합니다.")
    List<String> imageUrls
) {}
