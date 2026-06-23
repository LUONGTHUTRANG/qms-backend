package org.example.ticket.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.ApiResponse;
import org.example.ticket.client.ManagementClient;
import org.example.ticket.client.dto.CustomerSegmentConfigDto;
import org.example.ticket.entity.Ticket;
import org.example.ticket.event.TicketCreatedEvent;
import org.example.ticket.event.TicketQueuedEvent;
import org.example.ticket.service.RedisQueueService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQueueSyncListener {

    private static final int DEFAULT_REFERENCE_TARGET_WAIT_MINUTES = 20;

    private final RedisQueueService queueService;
    private final ManagementClient managementClient;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void processTicketCreationScore(TicketCreatedEvent event) {
        Ticket ticket = event.getTicket();

        try {
            CustomerSegmentConfigDto segment = null;
            ApiResponse<CustomerSegmentConfigDto> segmentResponse =
                    managementClient.getCustomerSegment(ticket.getCustomerSegmentId());
            if (segmentResponse != null) {
                segment = segmentResponse.getData();
            }

            int segmentCreditMinutes = calculateSegmentCreditMinutes(segment);
            int carryOverMinutes = ticket.getCarryOverMinutes() != null ? ticket.getCarryOverMinutes() : 0;
            LocalDateTime queueEnteredAt = ticket.getLastQueueEnteredAt() != null
                    ? ticket.getLastQueueEnteredAt()
                    : (ticket.getCreatedAt() != null ? ticket.getCreatedAt() : LocalDateTime.now());

            double virtualQueueScore = queueEnteredAt.atZone(ZoneId.systemDefault()).toEpochSecond()
                    - (long) (segmentCreditMinutes + carryOverMinutes) * 60;

            queueService.addTicketToQueue(
                    ticket.getBranchId(),
                    ticket.getRequestGroupId(),
                    ticket.getCustomerSegmentId(),
                    ticket.getId(),
                    virtualQueueScore
            );

            double initialPriorityMinutes = segmentCreditMinutes + carryOverMinutes;
            eventPublisher.publishEvent(new TicketQueuedEvent(this, ticket, initialPriorityMinutes));

            log.info(
                    "Queued ticket {} with virtualQueueScore={}, segmentCreditMinutes={}, carryOverMinutes={}",
                    ticket.getTicketNo(),
                    virtualQueueScore,
                    segmentCreditMinutes,
                    carryOverMinutes
            );
        } catch (Exception e) {
            log.error("Failed to process queue ticket for [{}]: {}", ticket.getTicketNo(), e.getMessage(), e);
        }
    }

    private int calculateSegmentCreditMinutes(CustomerSegmentConfigDto segment) {
        if (segment == null || segment.getTargetWaitMinutes() == null) {
            return 0;
        }

        int referenceTargetWait = getReferenceTargetWaitMinutes();
        return Math.max(0, referenceTargetWait - segment.getTargetWaitMinutes());
    }

    private int getReferenceTargetWaitMinutes() {
        try {
            ApiResponse<List<CustomerSegmentConfigDto>> response = managementClient.getCustomerSegments();
            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                return DEFAULT_REFERENCE_TARGET_WAIT_MINUTES;
            }

            return response.getData().stream()
                    .map(CustomerSegmentConfigDto::getTargetWaitMinutes)
                    .filter(value -> value != null && value > 0)
                    .max(Comparator.naturalOrder())
                    .orElse(DEFAULT_REFERENCE_TARGET_WAIT_MINUTES);
        } catch (Exception e) {
            return DEFAULT_REFERENCE_TARGET_WAIT_MINUTES;
        }
    }
}
