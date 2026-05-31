package org.example.auth.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.auth.event.CounterSessionStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener xử lý sự kiện CounterSessionStartedEvent
 * Phát đi thông báo qua Redis Pub/Sub khi một counter session được bắt đầu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CounterSessionStartedListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Khi một counter session được bắt đầu, phát sự kiện này qua Redis
     * Channel: qms:events:counter:session:{branchId}:{counterId}
     * Payload sẽ chứa:
     * - action: SESSION_STARTED
     * - counterId: ID của quầy
     * - userId: ID của người phục vụ
     * - fullName: Tên người phục vụ
     * - branchId: ID chi nhánh
     * - startedAt: Thời điểm bắt đầu
     */
    @Async
    @EventListener
    public void handleCounterSessionStarted(CounterSessionStartedEvent event) {
        try {
            String branchId = event.getSession().getBranchId().toString();
            String counterId = event.getSession().getCounterId().toString();
            String channel = "qms:events:counter:session:" + branchId + ":" + counterId;

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "SESSION_STARTED");
            payload.put("counterId", event.getSession().getCounterId());
            payload.put("userId", event.getSession().getUser().getId());
            payload.put("fullName", event.getFullName());
            payload.put("branchId", event.getSession().getBranchId());
            payload.put("startedAt", event.getSession().getStartedAt());

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(channel, json);

            log.info("Broadcasted SESSION_STARTED for counter {} with user {} to channel {}",
                    counterId, event.getFullName(), channel);
        } catch (Exception e) {
            log.error("Error broadcasting counter session started event via Redis Pub/Sub: {}", e.getMessage(), e);
        }
    }
}

