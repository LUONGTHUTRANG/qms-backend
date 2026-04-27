package org.example.realtime.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPubSubListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    // Hàm Callback này được gọi TỰ ĐỘNG mỗi khi có bất kỳ Service nào (vd qms-ticket)
    // ném json string vào chung kênh pub/sub của hệ sinh thái Redis
    @Override
    public void onMessage(Message message, byte[] pattern) {
        handleRedisMessage(message);
    }

    public void handleRedisMessage(Message message) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);

            // Cấu trúc channel: qms:events:branch:{branchId}:{groupId}:{segmentId}
            String[] parts = channel.split(":");
            String branchId = parts[3];
            String groupId = parts[4];
            String segmentId = parts[5];

            // Địa chỉ Đích mà Client Angular/ReactJS đang Subscribe trên Socket
            String webSocketTopic = "/topic/branch/" + branchId + "/" + groupId + "/" + segmentId;

            // Bắn ngay data (payload) xuống WebSocket
            messagingTemplate.convertAndSend(webSocketTopic, payload);

            log.info("FORWARDED REDIS => WEBSOCKET | Channel: {} | Target: {} | Payload: {}", channel, webSocketTopic, payload);
        } catch (Exception ex) {
            log.error("Failed to forward Redis pub/sub message to websocket: {}", ex.getMessage(), ex);
        }
    }
}
