package org.example.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${API_KEY:kiosk-secret-key-12345}")
    private String kioskApiKey;

    @Value("${DISPLAY_API_KEY:display-secret-key-67890}")
    private String displayApiKey;

    // Public endpoints that do not require JWT or API key.
    private final List<String> openApiPatterns = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/ticket/tickets/tracking/**",
            "/ws",
            "/ws/**"
    );

    private final List<String> kioskApiEndpoints = List.of(
            "/api/v1/ticket/tickets/create",
            "/api/v1/management"
    );

    private final List<String> displayApiEndpoints = List.of(
            "/api/v1/ticket/tickets/counters-current",
            "/api/v1/management",
            "/api/v1/ticket/tickets/list-by-status",
            "/api/v1/auth/counter-sessions/active/by-counter"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isSecured(request)) {
            List<String> apiKeyHeaders = request.getHeaders().get("X-API-Key");
            if (apiKeyHeaders != null && !apiKeyHeaders.isEmpty()) {
                String providedKey = apiKeyHeaders.get(0);
                boolean isAuthorized = false;

                if (kioskApiKey.equals(providedKey)) {
                    isAuthorized = kioskApiEndpoints.stream().anyMatch(path::startsWith);
                } else if (displayApiKey.equals(providedKey)) {
                    isAuthorized = displayApiEndpoints.stream().anyMatch(path::startsWith);
                }

                if (!isAuthorized) {
                    return onError(exchange, "API Key is invalid or not allowed for this endpoint", HttpStatus.FORBIDDEN);
                }

                ServerHttpRequest modifiedRequest = exchange.getRequest();
                boolean hasValidUser = false;

                if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        if (jwtUtil.isTokenValid(token)) {
                            Claims claims = jwtUtil.extractAllClaims(token);
                            String username = claims.getSubject();
                            String role = claims.get("role", String.class);
                            Object branchId = claims.get("branchId");
                            Object userId = claims.get("userId");

                            modifiedRequest = exchange.getRequest().mutate()
                                    .header("X-Auth-Username", username)
                                    .header("X-Auth-Role", role)
                                    .header("X-Auth-Branch-Id", branchId != null ? branchId.toString() : "")
                                    .header("X-Auth-User-Id", userId != null ? userId.toString() : "")
                                    .build();
                            hasValidUser = true;
                        }
                    }
                }

                if (!hasValidUser) {
                    modifiedRequest = exchange.getRequest().mutate()
                            .header("X-Auth-User-Id", "")
                            .build();
                }

                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            }

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Missing Authorization Header or Valid API Key", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Invalid Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);
            if (!jwtUtil.isTokenValid(token)) {
                return onError(exchange, "Invalid or expired JWT token", HttpStatus.UNAUTHORIZED);
            }

            Claims claims = jwtUtil.extractAllClaims(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            Object branchId = claims.get("branchId");
            Object userId = claims.get("userId");

            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-Auth-Username", username)
                    .header("X-Auth-Role", role)
                    .header("X-Auth-Branch-Id", branchId != null ? branchId.toString() : "")
                    .header("X-Auth-User-Id", userId != null ? userId.toString() : "")
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }

        return chain.filter(exchange);
    }

    private boolean isSecured(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return openApiPatterns.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> onError(ServerWebExchange exchange, String errorMessage, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> apiResponse = ApiResponse.error(httpStatus.value(), errorMessage);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(apiResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
