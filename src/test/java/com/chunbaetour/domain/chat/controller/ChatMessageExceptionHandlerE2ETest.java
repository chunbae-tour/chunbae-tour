package com.chunbaetour.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.chat.dto.request.ChatSendMessageRequest;
import com.chunbaetour.domain.chat.dto.response.StompErrorResponse;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * STOMP @MessageExceptionHandler E2E 검증.
 * BusinessException 발생 시 발신자의 /user/queue/errors로 에러 응답이 라우팅되는지 확인.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatMessageExceptionHandlerE2ETest extends AbstractIntegrationTest {

    @LocalServerPort int port;
    @Autowired private TokenIssuer tokenIssuer;

    private StompSession session;

    // 테스트 간 격리 — STOMP 세션 연결 해제
    @AfterEach
    void disconnectSession() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    // 비참여 채팅방 메시지 전송 → CHAT_005 에러가 /user/queue/errors로 수신
    @Test
    void sendToNonJoinedRoom_errorRoutedToUserQueue() throws Exception {
        String token = tokenIssuer.issueAccess(999999L, Role.USER, "test@test.com");
        BlockingQueue<StompErrorResponse> errors = new LinkedBlockingQueue<>();

        session = connect(token);

        session.subscribe("/user/queue/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return StompErrorResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add((StompErrorResponse) payload);
            }
        });
        // 구독 프레임이 서버에 등록된 후 SEND — 명시적 대기로 flaky 방지
        Thread.sleep(200);

        // 비참여 채팅방으로 메시지 전송 → CHAT_NOT_JOINED 예외 → @MessageExceptionHandler
        session.send("/pub/chat/rooms/999999/messages", new ChatSendMessageRequest("테스트"));

        StompErrorResponse error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.errorCode()).isEqualTo("CHAT_005");
    }

    // 유효 Principal + 비참여 방 → CHAT_005가 /user/queue/errors로 라우팅됨 (NullPointer 없음)
    @Test
    void sendWithValidPrincipal_noNullPointerFromRouting() throws Exception {
        String token = tokenIssuer.issueAccess(888888L, Role.USER, "test2@test.com");
        BlockingQueue<StompErrorResponse> errors = new LinkedBlockingQueue<>();

        session = connect(token);
        session.subscribe("/user/queue/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return StompErrorResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add((StompErrorResponse) payload);
            }
        });
        // 구독 프레임이 서버에 등록된 후 SEND — 명시적 대기로 flaky 방지
        Thread.sleep(200);

        // 유효 principal이면 CHAT_005만 오고 라우팅 오류 없음
        session.send("/pub/chat/rooms/888888/messages", new ChatSendMessageRequest("테스트"));

        StompErrorResponse error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.errorCode()).isEqualTo("CHAT_005");
    }

    // SockJsClient + JWT로 STOMP 세션 연결 후 반환
    private StompSession connect(String token) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        httpHeaders.setOrigin("http://localhost:3000");

        return stompClient.connectAsync(
                "http://localhost:" + port + "/ws-stomp",
                httpHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(10, TimeUnit.SECONDS);
    }
}
