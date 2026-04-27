package org.example.management.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.example.management.dto.CustomerSegmentCheckRequest;
import org.example.management.dto.CustomerSegmentCheckResponse;
import org.example.management.service.PriorityCustomerMockService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management/priority-customers")
@RequiredArgsConstructor
public class PriorityCustomerMockController {

    private final PriorityCustomerMockService service;

    @PostMapping("/check-segment")
    public ApiResponse<CustomerSegmentCheckResponse> checkCustomerSegment(@Valid @RequestBody CustomerSegmentCheckRequest request) {
        CustomerSegmentCheckResponse response = service.checkCustomerSegment(request);
        
        if (response.isValid()) {
            return ApiResponse.success(response, "Customer verified successfully");
        } else {
            return ApiResponse.success(response, "Customer NOT found or does not belong to the selected segment");
        }
    }
}

