package org.example.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketCreateRequest {
    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "Prefix code is required (e.g. A, B, C)")
    private String prefixCode;

    @NotNull(message = "Request Group ID is required")
    private Long requestGroupId;

    private Long serviceTypeId;

    @NotNull(message = "Customer Segment ID is required")
    private Long customerSegmentId;

    private String phoneNumber;
}

