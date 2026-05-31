package org.example.auth.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.auth.dto.CounterSessionDto;
import org.example.auth.service.CounterSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.session.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SessionCleanupScheduler {

    private final CounterSessionService sessionService;
    private final CacheManager cacheManager;

    /**
     * Scheduled task to end all active sessions and clear Redis cache
     * Runs daily at 00:00 (midnight) by default, configurable via cron expression
     */
    @Scheduled(cron = "${app.session.cleanup.schedule:0 0 0 * * ?}")
    public void dailySessionCleanup() {
        log.info("=== Starting daily session cleanup and Redis cache clear ===");

        try {
            // Log current active sessions before cleanup
            long activeSessionCount = sessionService.getActiveSessionCount();
            log.info("Found {} active sessions before cleanup", activeSessionCount);

            // End all active sessions
            List<CounterSessionDto> endedSessions = sessionService.endAllActiveSessions();
            log.info("Successfully ended {} sessions", endedSessions.size());

            if (!endedSessions.isEmpty()) {
                log.debug("Ended sessions details: {}", endedSessions);
            }

            // Clear Redis cache
            clearRedisCache();
            log.info("Redis cache cleared successfully");

            log.info("=== Daily session cleanup completed successfully ===");
        } catch (Exception e) {
            log.error("Error during daily session cleanup", e);
            // Don't throw - we want to ensure this doesn't break the application
        }
    }

    /**
     * Clear all Redis caches
     */
    private void clearRedisCache() {
        try {
            // Clear all named caches
            if (cacheManager != null) {
                for (String cacheName : cacheManager.getCacheNames()) {
                    cacheManager.getCache(cacheName).clear();
                    log.debug("Cleared cache: {}", cacheName);
                }
                log.info("All caches cleared successfully");
            }
        } catch (Exception e) {
            log.warn("Failed to clear Redis cache - this might be expected if Redis is not running", e);
            // Don't throw - continue with session cleanup even if Redis fails
        }
    }

    /**
     * Clear specific cache by name
     */
    public void clearCacheByName(String... cacheNames) {
        try {
            for (String cacheName : cacheNames) {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                    log.info("Cleared cache: {}", cacheName);
                } else {
                    log.warn("Cache not found: {}", cacheName);
                }
            }
        } catch (Exception e) {
            log.warn("Error clearing cache by name", e);
        }
    }
}


