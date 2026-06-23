package org.example.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.TicketEvent;
import org.example.ticket.entity.enums.TicketEventType;
import org.example.ticket.entity.enums.TicketStatus;
import org.example.ticket.event.TicketStatusChangedEvent;
import org.example.ticket.repository.TicketEventRepository;
import org.example.ticket.repository.TicketRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketSchedulerService {

    private final TicketRepository ticketRepository;
    private final TicketEventRepository ticketEventRepository;
    private final RedisQueueService redisQueueService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Chạy mỗi 1 phút để xử lý các vé SKIPPED_HOLD đã hết hạn 15 phút
     * Chính sách 3b: Vé hết thời gian tạm hoãn → chuyển thành SKIPPED_EXPIRED (hủy vé)
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000) // 1 phút
    @Transactional
    public void processExpiredSkipHoldTickets() {
        try {
            long currentMillis = java.time.Instant.now().toEpochMilli();
            java.util.Set<String> expiredTicketIdsStr = redisQueueService.getExpiredTickets(currentMillis);

            if (expiredTicketIdsStr == null || expiredTicketIdsStr.isEmpty()) {
                log.debug("No expired SKIPPED_HOLD tickets found in Redis");
                return;
            }

            List<Long> ticketIds = expiredTicketIdsStr.stream()
                    .map(Long::valueOf)
                    .collect(java.util.stream.Collectors.toList());

            List<Ticket> expiredTickets = ticketRepository.findAllById(ticketIds);

            if (expiredTickets.isEmpty()) {
                log.debug("No matching tickets found in DB for expired Redis keys");
                // Clean up stale Redis keys
                ticketIds.forEach(redisQueueService::removeTicketFromExpireQueue);
                return;
            }

            log.info("Found {} expired SKIPPED_HOLD tickets to process", expiredTickets.size());

            int processedCount = 0;
            for (Ticket ticket : expiredTickets) {
                if (ticket.getStatus() == TicketStatus.SKIPPED_HOLD) {
                    processExpiredTicket(ticket);
                    processedCount++;
                }
                // Always remove from expire queue after checking/processing
                redisQueueService.removeTicketFromExpireQueue(ticket.getId());
            }

            log.info("Successfully processed {} expired SKIPPED_HOLD tickets", processedCount);

        } catch (Exception e) {
            log.error("Error processing expired SKIPPED_HOLD tickets: {}", e.getMessage(), e);
        }
    }

    /**
     * Xử lý một vé hết hạn tạm hoãn
     */
    private void processExpiredTicket(Ticket ticket) {
        try {
            TicketStatus oldStatus = ticket.getStatus();

            // Cập nhật status thành SKIPPED_EXPIRED
            ticket.setStatus(TicketStatus.SKIPPED_EXPIRED);
            ticket = ticketRepository.save(ticket);

            log.info("Ticket {} expired after SKIPPED_HOLD, changed to SKIPPED_EXPIRED", ticket.getTicketNo());

            // Xóa vé khỏi suspend queue
            redisQueueService.removeTicketFromSuspendQueue(
                    ticket.getBranchId(),
                    ticket.getRequestGroupId(),
                    ticket.getCustomerSegmentId(),
                    ticket.getId()
            );

            // Lưu event vào database
            TicketEvent event = TicketEvent.builder()
                    .ticket(ticket)
                    .eventType(TicketEventType.SKIPPED_EXPIRED)
                    .fromStatus(oldStatus)
                    .toStatus(TicketStatus.SKIPPED_EXPIRED)
                    .note("Auto-expired after 15 minutes in SKIPPED_HOLD status")
                    .build();
            ticketEventRepository.save(event);

            // Publish event để notify UI/listeners
            eventPublisher.publishEvent(new TicketStatusChangedEvent(this, ticket, oldStatus));

            log.debug("Ticket {} successfully processed - removed from suspend queue and event published", ticket.getTicketNo());

        } catch (Exception e) {
            log.error("Failed to process expired ticket {}: {}", ticket.getTicketNo(), e.getMessage(), e);
        }
    }
}
