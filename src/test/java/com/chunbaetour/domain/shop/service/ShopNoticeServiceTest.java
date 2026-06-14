package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.shop.dto.request.ShopNoticeCreateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopNoticeResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopNotice;
import com.chunbaetour.domain.shop.repository.ShopNoticeRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShopNoticeServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private ShopNoticeRepository shopNoticeRepository;

    @InjectMocks
    private ShopNoticeService shopNoticeService;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;
    private static final Long NOTICE_ID = 100L;

    private Shop createActiveShop() {
        Shop shop = Shop.builder()
                .userId(USER_ID)
                .applicationId(1L)
                .shopName("춘배 가게")
                .category("FOOD")
                .address("서울시 종로구")
                .build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        return shop;
    }

    private Shop createSuspendedShop() {
        Shop shop = createActiveShop();
        shop.hide();
        return shop;
    }

    private ShopNotice createNotice() {
        ShopNotice notice = ShopNotice.create(SHOP_ID, "공지 제목", "공지 내용");
        ReflectionTestUtils.setField(notice, "id", NOTICE_ID);
        return notice;
    }

    private ShopNotice noticeWithId(long id) {
        ShopNotice notice = ShopNotice.create(SHOP_ID, "공지 제목", "공지 내용");
        ReflectionTestUtils.setField(notice, "id", id);
        return notice;
    }

    // ── POST /merchants/me/shops/{shopId}/notices ──────────────────────────

    @Nested
    @DisplayName("공지 등록")
    class CreateNotice {

        @Test
        @DisplayName("성공")
        void createNotice_success() {
            Shop shop = createActiveShop();
            ShopNotice notice = createNotice();
            ShopNoticeCreateRequest request = new ShopNoticeCreateRequest("공지 제목", "공지 내용");

            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
            given(shopNoticeRepository.save(any(ShopNotice.class))).willReturn(notice);

            ShopNoticeResponse response = shopNoticeService.createNotice(USER_ID, SHOP_ID, request);

            assertThat(response.noticeId()).isEqualTo(NOTICE_ID);
            assertThat(response.title()).isEqualTo("공지 제목");
        }

        @Test
        @DisplayName("가게 없음 — SHOP_NOT_FOUND")
        void createNotice_shopNotFound() {
            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shopNoticeService.createNotice(USER_ID, SHOP_ID,
                    new ShopNoticeCreateRequest("제목", "내용")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
        }

        @Test
        @DisplayName("SUSPENDED 가게 — SHOP_INACTIVE")
        void createNotice_suspendedShop() {
            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID))
                    .willReturn(Optional.of(createSuspendedShop()));

            assertThatThrownBy(() -> shopNoticeService.createNotice(USER_ID, SHOP_ID,
                    new ShopNoticeCreateRequest("제목", "내용")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_INACTIVE);
        }
    }

    // ── GET /merchants/me/shops/{shopId}/notices ───────────────────────────

    @Nested
    @DisplayName("공지 목록 조회")
    class GetNotices {

        @Test
        @DisplayName("첫 페이지 조회 성공 (cursor 없음)")
        void getNotices_firstPage() {
            Shop shop = createActiveShop();
            List<ShopNotice> notices = List.of(createNotice());

            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
            given(shopNoticeRepository.findByShopIdOrderByIdDesc(any(), any())).willReturn(notices);

            CursorPageResponse<ShopNoticeResponse> response =
                    shopNoticeService.getNotices(USER_ID, SHOP_ID, null, 20);

            assertThat(response.content()).hasSize(1);
            assertThat(response.hasNext()).isFalse();
        }

        @Test
        @DisplayName("다음 페이지 존재 — hasNext=true, 절단, nextCursor는 노출 마지막 항목 id")
        void getNotices_hasNext_truncatesAndSetsCursor() {
            Shop shop = createActiveShop();
            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
            // size=2 요청, size+1=3개 반환 → hasNext=true
            given(shopNoticeRepository.findByShopIdOrderByIdDesc(any(), any()))
                    .willReturn(List.of(noticeWithId(30L), noticeWithId(20L), noticeWithId(10L)));

            CursorPageResponse<ShopNoticeResponse> response =
                    shopNoticeService.getNotices(USER_ID, SHOP_ID, null, 2);

            assertThat(response.content()).hasSize(2);
            assertThat(response.hasNext()).isTrue();
            // 노출 마지막 항목(id=20) 인코딩 — sentinel(id=10) 아님
            assertThat(CursorUtils.decode(response.nextCursor())).isEqualTo(20L);
        }

        @Test
        @DisplayName("가게 없음 — SHOP_NOT_FOUND")
        void getNotices_shopNotFound() {
            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shopNoticeService.getNotices(USER_ID, SHOP_ID, null, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
        }

        @Test
        @DisplayName("size = 0 — INVALID_INPUT_VALUE")
        void getNotices_sizeZero_throws() {
            assertThatThrownBy(() -> shopNoticeService.getNotices(USER_ID, SHOP_ID, null, 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("size > 100 — INVALID_INPUT_VALUE")
        void getNotices_sizeOverLimit_throws() {
            assertThatThrownBy(() -> shopNoticeService.getNotices(USER_ID, SHOP_ID, null, 101))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // ── DELETE /merchants/me/shops/{shopId}/notices/{noticeId} ─────────────

    @Nested
    @DisplayName("공지 삭제")
    class DeleteNotice {

        @Test
        @DisplayName("성공")
        void deleteNotice_success() {
            Shop shop = createActiveShop();
            ShopNotice notice = createNotice();

            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
            given(shopNoticeRepository.findByIdAndShopId(NOTICE_ID, shop.getId()))
                    .willReturn(Optional.of(notice));

            shopNoticeService.deleteNotice(USER_ID, SHOP_ID, NOTICE_ID);

            then(shopNoticeRepository).should().delete(notice);
        }

        @Test
        @DisplayName("공지 없음 — SHOP_NOTICE_NOT_FOUND")
        void deleteNotice_noticeNotFound() {
            Shop shop = createActiveShop();
            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
            given(shopNoticeRepository.findByIdAndShopId(NOTICE_ID, shop.getId()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> shopNoticeService.deleteNotice(USER_ID, SHOP_ID, NOTICE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_NOTICE_NOT_FOUND);
        }

        @Test
        @DisplayName("SUSPENDED 가게 — SHOP_INACTIVE")
        void deleteNotice_suspendedShop() {
            given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID))
                    .willReturn(Optional.of(createSuspendedShop()));

            assertThatThrownBy(() -> shopNoticeService.deleteNotice(USER_ID, SHOP_ID, NOTICE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_INACTIVE);
        }
    }
}
