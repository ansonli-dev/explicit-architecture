package {{BASE_PACKAGE}}.interfaces.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class {{CHANNEL}}StompReceiveIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired
    {{PUBLISHER}} publisher;

    @Test
    void subscriber_receives_pushed_message() throws Exception {
        var stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        var received = new LinkedBlockingQueue<{{MESSAGE_CLASS}}>();

        var session = stompClient
            .connectAsync("ws://localhost:%d/ws".formatted(port), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/{{CHANNEL_PATH}}/{{RECIPIENT_ID}}", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return {{MESSAGE_CLASS}}.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(({{MESSAGE_CLASS}}) payload);
            }
        });

        publisher.publishToRecipient("{{RECIPIENT_ID}}", new {{MESSAGE_CLASS}}("{{MESSAGE_TYPE}}"));

        var message = received.poll(5, TimeUnit.SECONDS);

        assertThat(message).isNotNull();
        assertThat(message.type()).isEqualTo("{{MESSAGE_TYPE}}");

        session.disconnect();
    }
}
