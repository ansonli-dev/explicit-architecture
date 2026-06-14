package {{BASE_PACKAGE}}.interfaces.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class {{CHANNEL}}WebSocketReceiveIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired
    {{PUBLISHER}} publisher;

    @Test
    void client_receives_pushed_message() throws Exception {
        var handler = new QueueingTextWebSocketHandler();
        var client = new StandardWebSocketClient();

        WebSocketSession session = client.execute(
            handler,
            "ws://localhost:%d/ws/{{CHANNEL_PATH}}?recipientId={{RECIPIENT_ID}}".formatted(port)
        ).get(5, TimeUnit.SECONDS);

        publisher.publishToRecipient("{{RECIPIENT_ID}}", "{{MESSAGE_TYPE}}");

        var message = handler.poll(5, TimeUnit.SECONDS);

        assertThat(message).contains("{{MESSAGE_TYPE}}");

        session.close();
    }

    static class QueueingTextWebSocketHandler extends TextWebSocketHandler {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        String poll(long timeout, TimeUnit unit) throws InterruptedException {
            return messages.poll(timeout, unit);
        }
    }
}
