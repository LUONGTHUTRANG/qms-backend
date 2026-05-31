package org.example.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.auth.context.UserContextHolder;
import org.example.auth.dto.CounterSessionCreateRequest;
import org.example.auth.dto.CounterSessionDto;
import org.example.auth.service.CounterSessionService;
import org.example.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/counter-sessions")
@RequiredArgsConstructor
public class CounterSessionController {

    private final CounterSessionService sessionService;

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CounterSessionDto> startSession(@Valid @RequestBody CounterSessionCreateRequest request) {
        // Because of UserContextInterceptor, we don't even need to inject @RequestHeader here!
        return ApiResponse.success(sessionService.startSession(request), "Counter session started successfully");
    }

    @GetMapping("/active/me")
    public ApiResponse<CounterSessionDto> getActiveSession() {
         Long userId = UserContextHolder.getUserId();
         return ApiResponse.success(sessionService.getActiveSessionByUserId(userId));
    }

    @PostMapping("/end")
    public ApiResponse<CounterSessionDto> endSession() {
        return ApiResponse.success(sessionService.endSession(), "Counter session ended successfully");
    }

    /**
     * Admin endpoint to manually end all active sessions and clear cache
     * (For debugging or manual intervention only)
     */
    @PostMapping("/admin/cleanup")
    public ApiResponse<Object> manualCleanup() {
        List<CounterSessionDto> endedSessions = sessionService.endAllActiveSessions();
        return ApiResponse.success(
            java.util.Map.of(
                "endedSessionCount", endedSessions.size(),
                "sessions", endedSessions
            ),
            "All active sessions cleaned up successfully"
        );
    }

    /**
     * Get count of active sessions (for monitoring)
     */
    @GetMapping("/active/count")
    public ApiResponse<Long> getActiveSessionCount() {
        return ApiResponse.success(sessionService.getActiveSessionCount(), "Active session count retrieved");
    }

     /**
      * Lấy danh sách counter IDs đang có phiên làm việc ACTIVE
      * API này được gọi bởi qms-management để xác định trạng thái thực tế của quầy
      */
     @GetMapping("/active/counter-ids")
     public ApiResponse<List<Long>> getActiveCounterIds() {
         return ApiResponse.success(sessionService.getActiveCounterIds(), "Active counter IDs retrieved");
     }

     /**
      * Lấy thông tin về người đang phục vụ counter có ID được chỉ định
      * Kiểm tra xem có session đang ACTIVE cho counter đó hay không
      * Request param: counterId - ID của quầy cần kiểm tra
      * Response: Thông tin session và người phục vụ nếu counter đang được phục vụ
      */
     @GetMapping("/active/by-counter")
     public ApiResponse<CounterSessionDto> getActiveSessionByCounter(@RequestParam("counterId") Long counterId) {
         return ApiResponse.success(sessionService.getActiveSessionByCounterId(counterId), "Counter session info retrieved successfully");
     }
}
