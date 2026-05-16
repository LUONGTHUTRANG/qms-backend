package org.example.management.client;

import org.example.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "qms-ticket", url = "http://localhost:8083")
public interface TicketServiceClient {

    @GetMapping("/api/v1/ticket/tickets/counters-current")
    ApiResponse<Map<Long, Map<String, Object>>> getTicketsForCounters(
            @RequestParam("counterIds") List<Long> counterIds
    );
}




