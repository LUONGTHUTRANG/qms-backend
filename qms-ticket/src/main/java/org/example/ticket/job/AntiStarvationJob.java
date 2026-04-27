package org.example.ticket.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticket.service.RedisQueueService;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AntiStarvationJob {

    private final RedisQueueService queueService;
    private final RedisTemplate<String, Object> redisTemplate;

    // Chính sách 2: Cộng điểm theo thời gian (Anti-Starvation Job) - 1 phút 1 lần
    @Scheduled(fixedRate = 60000)
    public void executeAntiStarvation() {
        Set<String> keys = queueService.getAllQueueKeys();

        if (keys == null || keys.isEmpty()) return;

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                Set<byte[]> ticketIds = connection.zSetCommands().zRange(key.getBytes(), 0, -1);
                if (ticketIds != null) {
                    for (byte[] tId : ticketIds) {
                        connection.zSetCommands().zIncrBy(key.getBytes(), -1.0, tId); // ZINCRBY -1 vi luong cham diem am -100 -50 
                    }
                }
            }
            return null;
        });
        
        log.info("Processed Anti-Starvation! Increased score (+1) for {} queue groups.", keys.size());
    }
}
