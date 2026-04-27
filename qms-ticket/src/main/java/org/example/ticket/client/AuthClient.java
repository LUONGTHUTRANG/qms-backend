package org.example.ticket.client;

import org.example.common.dto.ApiResponse;
import org.example.ticket.client.dto.CounterSessionDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "qms-auth", url = "http://localhost:8081")
public interface AuthClient {

    @GetMapping("/api/v1/auth/counter-sessions/active/me")
    ApiResponse<CounterSessionDto> getActiveSession(@RequestHeader("X-Auth-User-Id") Long userId);
}
