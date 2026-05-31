package org.example.management.client;

import org.example.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client để gọi qms-auth service
 * Lấy thông tin phiên làm việc (Counter Session) của các quầy
 */
@FeignClient(name = "qms-auth", url = "http://localhost:8081")
public interface AuthServiceClient {

    /**
     * Lấy danh sách ID quầy đang có phiên làm việc ACTIVE trong ngày hiện tại
     * @return List các counter ID đang có phiên ACTIVE
     */
    @GetMapping("/api/v1/auth/counter-sessions/active/counter-ids")
    ApiResponse<List<Long>> getActiveCounterIds();
}

