package org.example.ticket.dto;

public record WaitEstimateDto(
        Integer estimatedWaitTime,
        int peopleAhead,
        int activeCounters,
        boolean serviceAvailable
) {
}
