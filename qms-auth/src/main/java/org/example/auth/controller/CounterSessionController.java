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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
}
