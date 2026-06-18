package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.shop.type.ShopImageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가게 사진 엔티티 (KAN-315, ADR-003).
 *
 * <p>{@code Shop.imageUrls} 단일 JSON blob을 대체하는 용도 분리 모델 — 사진마다 용도({@link ShopImageType})·
 * 정렬순서·대표플래그를 행 단위로 표현한다. 업로드 1단계화(업로드 즉시 행 생성)로 "업로드 후 자동반영" 갭을 해소한다.
 *
 * <p>{@code object_key}는 S3 객체 키(예: {@code shops/10/uuid.jpg})를 저장한다 — 만료되는 presigned URL이
 * 아니라 키를 영속화하고, 조회 시 기존 presign 파이프라인으로 변환한다(ShopService 패턴 재사용).
 */
@Entity
@Table(
        name = "shop_images",
        indexes = {
                // 가게별 조회(목록 정렬·소유권 검증) 주 경로
                @Index(name = "idx_shop_images_shop_id_id", columnList = "shop_id, id"),
                // 고아 reconcile(existsByObjectKey) 풀스캔 방지 + 키 중복 방지(UUID라 자연 유일) (KAN-319)
                @Index(name = "uq_shop_images_object_key", columnList = "object_key", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    // S3 객체 키 (예: shops/10/uuid.jpg). 조회 시 presigned GET URL로 변환.
    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShopImageType type;

    // 갤러리 정렬 순서 (PROFILE은 0 고정, GALLERY는 추가 순서대로 증가)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 대표 사진 여부 — PROFILE=true, GALLERY=false. 향후 갤러리 내 대표 지정 확장 여지.
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Builder
    private ShopImage(Long shopId, String objectKey, ShopImageType type, int sortOrder, boolean isPrimary) {
        this.shopId = shopId;
        this.objectKey = objectKey;
        this.type = type;
        this.sortOrder = sortOrder;
        this.isPrimary = isPrimary;
    }

    /** 대표(PROFILE) 사진 — 가게당 1장, sort_order 0, is_primary=true. */
    public static ShopImage profile(Long shopId, String objectKey) {
        return ShopImage.builder()
                .shopId(shopId)
                .objectKey(objectKey)
                .type(ShopImageType.PROFILE)
                .sortOrder(0)
                .isPrimary(true)
                .build();
    }

    /** 갤러리(GALLERY) 사진 — 다중, 호출자가 다음 sort_order 지정. */
    public static ShopImage gallery(Long shopId, String objectKey, int sortOrder) {
        return ShopImage.builder()
                .shopId(shopId)
                .objectKey(objectKey)
                .type(ShopImageType.GALLERY)
                .sortOrder(sortOrder)
                .isPrimary(false)
                .build();
    }
}
