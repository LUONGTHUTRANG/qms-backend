package org.example.ticket.client;

import org.example.common.dto.ApiResponse;
import org.example.ticket.client.dto.CustomerSegmentConfigDto;
import org.example.ticket.client.dto.ServiceCounterDto;
import org.example.ticket.client.dto.ServiceTypeConfigDto;
import org.example.ticket.client.dto.RequestGroupDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Gọi trực tiếp đến Management service (hoặc qua Gateway cũng được)
@FeignClient(name = "qms-management", url = "http://localhost:8082")
public interface ManagementClient {

    @GetMapping("/api/v1/management/customer-segments/{id}")
    ApiResponse<CustomerSegmentConfigDto> getCustomerSegment(@PathVariable("id") Long id);

    @GetMapping("/api/v1/management/service-types/{id}")
    ApiResponse<ServiceTypeConfigDto> getServiceType(@PathVariable("id") Long id);

    @GetMapping("/api/v1/management/service-counters/{id}")
    ApiResponse<ServiceCounterDto> getServiceCounter(@PathVariable("id") Long id);

    @GetMapping("/api/v1/management/request-groups/{id}")
    ApiResponse<RequestGroupDto> getRequestGroup(@PathVariable("id") Long id);
}
