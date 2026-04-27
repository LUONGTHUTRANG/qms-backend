package org.example.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisQueueService {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String QUEUE_PREFIX = "qms:queue:";

    public String buildQueueKey(Long branchId, Long requestGroupId, Long segmentId) {
        return QUEUE_PREFIX + branchId + ":" + requestGroupId + ":" + (segmentId != null ? segmentId : "0");
    }

    public void addTicketToQueue(Long branchId, Long requestGroupId, Long segmentId, Long ticketId, double score) {
        String key = buildQueueKey(branchId, requestGroupId, segmentId);
        redisTemplate.opsForZSet().add(key, ticketId.toString(), score);
    }

    public void removeTicketFromQueue(Long branchId, Long requestGroupId, Long segmentId, Long ticketId) {
        String key = buildQueueKey(branchId, requestGroupId, segmentId);
        redisTemplate.opsForZSet().remove(key, ticketId.toString());
    }

    public Double getTicketScore(Long branchId, Long requestGroupId, Long segmentId, Long ticketId) {
        String key = buildQueueKey(branchId, requestGroupId, segmentId);
        return redisTemplate.opsForZSet().score(key, ticketId.toString());
    }

    public Set<ZSetOperations.TypedTuple<Object>> getTicketsInQueue(Long branchId, Long requestGroupId, Long segmentId) {
        String key = buildQueueKey(branchId, requestGroupId, segmentId);
        // Vì Điểm đang được lưu ở dạng Âm (Ví dụ điểm 150 thì lưu -150).
        // => Cần lấy bé nhất đến lớn nhất (Tất là Âm vô cực -> Dương vô cực ====> Những người Vip điểm to sẽ nằm trên đầu)
        return redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
    }

    // API Cho Policy Chống Đói (Tăng ++ cho Toàn bộ queue)
    public Set<String> getAllQueueKeys() {
        return redisTemplate.keys(QUEUE_PREFIX + "*");
    }

    public void clearAllQueues() {
        Set<String> keys = getAllQueueKeys();
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

