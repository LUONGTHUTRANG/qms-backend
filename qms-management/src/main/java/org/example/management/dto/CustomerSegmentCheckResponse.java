package org.example.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSegmentCheckResponse {
    private boolean isValid;
    private String fullName; // Can be returned if valid, null otherwise
}

