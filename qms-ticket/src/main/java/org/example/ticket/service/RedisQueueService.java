package org.example.ticket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisQueueService {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String QUEUE_PREFIX = "qms:queue:";
    private static final String SUSPEND_QUEUE_PREFIX = "qms:suspend_queue:";
    private static final long TTL_HOURS = 48;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private String getDateSuffix() {
        return DATE_FORMATTER.format(LocalDate.now());
    }

    public String buildQueueKey(Long branchId, Long requestGroupId, Long segmentId) {
        return QUEUE_PREFIX + getDateSuffix() + ":" + branchId + ":" + requestGroupId + ":" + (segmentId != null ? segmentId : "0");
    }

    public String buildSuspendQueueKey(Long branchId, Long requestGroupId, Long segmentId) {
        return SUSPEND_QUEUE_PREFIX + getDateSuffix() + ":" + branchId + ":" + requestGroupId + ":" + (segmentId != null ? segmentId : "0");
    }

    public void addTicketToQueue(Long branchId, Long requestGroupId, Long segmentId, Long ticketId, double score) {
        String key = buildQueueKey(branchId, requestGroupId, segmentId);
        redisTemplate.opsForZSet().add(key, ticketId.toString(), score);
        redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
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

    // Methods for Suspend Queue (SKIPPED_HOLD tickets) - Using Hash instead of ZSet
    // Structure: Hash with field=ticketId, value=score
    public void addTicketToSuspendQueue(Long branchId, Long requestGroupId, Long segmentId, Long ticketId, double score) {
        String key = buildSuspendQueueKey(branchId, requestGroupId, segmentId);
        redisTemplate.opsForHash().put(key, ticketId.toString(), String.valueOf(score));
        redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
    }

    public void removeTicketFromSuspendQueue(Long branchId, Long requestGroupId, Long segmentId, Long ticketId) {
        String key = buildSuspendQueueKey(branchId, requestGroupId, segmentId);
        redisTemplate.opsForHash().delete(key, ticketId.toString());
    }

    public Double getTicketScoreInSuspendQueue(Long branchId, Long requestGroupId, Long segmentId, Long ticketId) {
        String key = buildSuspendQueueKey(branchId, requestGroupId, segmentId);
        Object value = redisTemplate.opsForHash().get(key, ticketId.toString());
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public Map<Object, Object> getTicketsInSuspendQueue(Long branchId, Long requestGroupId, Long segmentId) {
        String key = buildSuspendQueueKey(branchId, requestGroupId, segmentId);
        return redisTemplate.opsForHash().entries(key);
    }

    public boolean isTicketInSuspendQueue(Long branchId, Long requestGroupId, Long segmentId, Long ticketId) {
        String key = buildSuspendQueueKey(branchId, requestGroupId, segmentId);
        return redisTemplate.opsForHash().hasKey(key, ticketId.toString());
    }

    // API Cho Policy Chống Đói (Tăng ++ cho Toàn bộ queue)
    public Set<String> getAllQueueKeys() {
        return redisTemplate.keys(QUEUE_PREFIX + "*");
    }

    public Set<String> getAllSuspendQueueKeys() {
        return redisTemplate.keys(SUSPEND_QUEUE_PREFIX + "*");
    }

    public void clearAllQueues() {
        Set<String> keys = getAllQueueKeys();
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void clearAllSuspendQueues() {
        Set<String> keys = getAllSuspendQueueKeys();
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

