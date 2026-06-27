package com.chunbaetour.domain.auth.profileimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.admin.user.dto.response.UserAdminDetailResponse;
import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.chat.dto.response.ChatMessageResponse;
import com.chunbaetour.domain.chat.dto.response.ChatRoomDetailResponse;
import com.chunbaetour.domain.chat.dto.response.ChatRoomMemberResponse;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.entity.Message;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.MessageType;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.companion.review.dto.response.CompanionReviewResponse;
import com.chunbaetour.domain.companion.review.entity.CompanionReview;
import com.chunbaetour.domain.place.PlaceReview;
import com.chunbaetour.domain.place.dto.response.PlaceReviewResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 교차도메인 표시 변환 검증 (KAN-320 #1) — 업로드한 "우리 키"가 각 도메인 응답 DTO에서 표시 URL로 변환되는지 고정.
 *
 * <p>왜 필요한가: 팀원 도메인 기존 테스트는 외부 URL/일반 문자열만 써서 {@code toDisplayUrl}의 passthrough만 탐.
 * "우리 키 → presign" 분기가 각 DTO 팩토리에서 실제로 호출되는지 검증하는 회귀 가드가 없었다. 본 테스트는
 * {@link ProfileImageDisplaySupport}를 변환 stub으로 초기화해 6개 표시 지점이 모두 브리지를 경유함을 증명한다.
 */
class ProfileImageDisplayConversionTest {

    private static final String OWN_KEY = "users/3/profile/11111111-1111-1111-1111-111111111111.jpg";
    private static final String CONVERTED = "https://signed.example/url";

    @BeforeEach
    void initBridge() {
        // 우리 키 → 변환값으로 매핑하는 stub으로 정적 브리지 초기화
        ProfileImageService stub = mock(ProfileImageService.class);
        lenient().when(stub.toDisplayUrl(OWN_KEY)).thenReturn(CONVERTED);
        lenient().when(stub.toDisplayUrl(null)).thenReturn(null);
        new ProfileImageDisplaySupport(stub).afterPropertiesSet();
    }

    @AfterEach
    void resetBridge() {
        // 정적 delegate 누수 방지 — 다른 테스트는 passthrough 폴백으로 복귀
        ReflectionTestUtils.setField(ProfileImageDisplaySupport.class, "delegate", null);
    }

    private Account account() {
        Account a = mock(Account.class);
        lenient().when(a.getId()).thenReturn(3L);
        lenient().when(a.getNickname()).thenReturn("닉");
        lenient().when(a.getProfileImageUrl()).thenReturn(OWN_KEY);
        lenient().when(a.getCompanionScore()).thenReturn(4.5);
        lenient().when(a.getEmail()).thenReturn("u@e.com");
        lenient().when(a.getRole()).thenReturn(Role.USER);
        lenient().when(a.getStatus()).thenReturn(AccountStatus.ACTIVE);
        return a;
    }

    @Test
    @DisplayName("community WriterInfo.from/fromComment — 우리 키 → 변환값")
    void writerInfo() {
        assertThat(WriterInfo.from(account()).profileImageUrl()).isEqualTo(CONVERTED);
        assertThat(WriterInfo.fromComment(account()).profileImageUrl()).isEqualTo(CONVERTED);
    }

    @Test
    @DisplayName("chat ChatRoomMemberResponse/MemberInfo/ChatMessageResponse — 우리 키 → 변환값")
    void chat() {
        ChatRoomMember member = mock(ChatRoomMember.class);
        lenient().when(member.getUserId()).thenReturn(3L);
        lenient().when(member.getMemberState()).thenReturn(ChatMemberState.MEMBER_ACTIVE);

        assertThat(ChatRoomMemberResponse.from(member, account()).profileImageUrl()).isEqualTo(CONVERTED);
        assertThat(ChatRoomDetailResponse.MemberInfo.from(member, account()).profileImageUrl()).isEqualTo(CONVERTED);

        Message message = mock(Message.class);
        lenient().when(message.getSenderId()).thenReturn(3L);
        lenient().when(message.getMessageType()).thenReturn(MessageType.TEXT);
        assertThat(ChatMessageResponse.from(message, account(), null).senderProfileImageUrl()).isEqualTo(CONVERTED);
    }

    @Test
    @DisplayName("companionreview CompanionReviewResponse — 우리 키 → 변환값")
    void companionReview() {
        CompanionReview review = mock(CompanionReview.class);
        lenient().when(review.getScore()).thenReturn(5);
        assertThat(CompanionReviewResponse.of(review, account()).reviewerProfileImageUrl()).isEqualTo(CONVERTED);
    }

    @Test
    @DisplayName("admin UserAdminDetailResponse — 우리 키 → 변환값")
    void adminDetail() {
        assertThat(UserAdminDetailResponse.from(account(), 0L).profileImageUrl()).isEqualTo(CONVERTED);
    }

    @Test
    @DisplayName("place PlaceReviewResponse — 우리 키 → 변환값")
    void placeReview() {
        Account author = account(); // when() 인자 안에서 stubbing하면 UnfinishedStubbing — 먼저 완성
        PlaceReview review = mock(PlaceReview.class);
        lenient().when(review.getAuthor()).thenReturn(author);
        lenient().when(review.getRating()).thenReturn(4);
        assertThat(PlaceReviewResponse.from(review, List.of()).authorProfileImageUrl()).isEqualTo(CONVERTED);
    }
}
