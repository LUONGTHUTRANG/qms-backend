package org.example.auth.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.auth.dto.CounterSessionDto;
import org.example.auth.service.CounterSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.session.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SessionCleanupApplicationListener implements ApplicationListener<ContextRefreshedEvent> {

    private final CounterSessionService sessionService;
    private final CacheManager cacheManager;
    private static boolean isInitialized = false;

    /**
     * Triggered when the application context is refreshed (after startup or restart)
     * This ensures all stale sessions from previous runs are cleaned up
     */
    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        // Prevent multiple triggers during context initialization
        if (isInitialized) {
            return;
        }
        isInitialized = true;

        log.info("=== Application started - Cleaning up stale sessions ===");

        try {
            // Log current active sessions
            long activeSessionCount = sessionService.getActiveSessionCount();
            log.info("Found {} active sessions from previous run", activeSessionCount);

            if (activeSessionCount > 0) {
                // End all active sessions from previous run
                List<CounterSessionDto> endedSessions = sessionService.endAllActiveSessions();
                log.warn("Forcefully ended {} stale sessions from previous run", endedSessions.size());

                endedSessions.forEach(session ->
                    log.debug("Ended stale session - User ID: {}, Counter ID: {}, Started at: {}",
                              session.getUserId(), session.getCounterId(), session.getStartedAt())
                );
            }

            // Clear Redis cache
            clearRedisCache();
            log.info("Redis cache cleared successfully on startup");

            log.info("=== Application startup cleanup completed successfully ===");
        } catch (Exception e) {
            log.error("Error during application startup session cleanup", e);
            // Don't throw - we want to ensure this doesn't prevent app startup
        }
    }

    /**
     * Clear all caches on application startup
     */
    private void clearRedisCache() {
        try {
            if (cacheManager != null) {
                for (String cacheName : cacheManager.getCacheNames()) {
                    var cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                        log.debug("Cleared cache on startup: {}", cacheName);
                    }
                }
                log.info("All caches flushed on startup");
            }
        } catch (Exception e) {
            log.warn("Failed to flush caches on startup - caching might not be fully initialized", e);
            // Continue anyway - caching is optional for the session cleanup to work
        }
    }
}


