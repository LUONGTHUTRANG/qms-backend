package org.example.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.auth.dto.AuthResponse;
import org.example.auth.dto.LoginRequest;
import org.example.auth.dto.RefreshRequest;
import org.example.auth.dto.UserInfoDto;
import org.example.auth.service.AuthService;
import org.example.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), "Login successful");
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refreshToken(request), "Token refreshed successfully");
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoDto> getUserInfo(@RequestHeader(value = "X-Auth-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new org.example.common.exception.BusinessException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Missing X-Auth-User-Id header. Please make sure you are calling the Gateway (port 8080) and not the Auth Service directly (port 8081)."
            );
        }
        return ApiResponse.success(authService.getUserInfo(userId), "User info retrieved successfully");
    }
}
