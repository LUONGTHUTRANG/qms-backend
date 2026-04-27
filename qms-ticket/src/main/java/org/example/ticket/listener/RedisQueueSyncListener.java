package org.example.ticket.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.ApiResponse;
import org.example.ticket.client.ManagementClient;
import org.example.ticket.client.dto.CustomerSegmentConfigDto;
import org.example.ticket.client.dto.ServiceTypeConfigDto;
import org.example.ticket.entity.Ticket;
import org.example.ticket.event.TicketCreatedEvent;
import org.example.ticket.event.TicketQueuedEvent;
import org.example.ticket.service.RedisQueueService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQueueSyncListener {

    private final RedisQueueService queueService;
    private final ManagementClient managementClient;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    public void processTicketCreationScore(TicketCreatedEvent event) {
        Ticket ticket = event.getTicket();
        Integer totalScore = 0;

        try {
            if (event.isTransfer()) {
                // Chính sách 5: Chuyển quầy
                totalScore = event.getOldTotalScore() + 50;
                log.info("Ticket {} transferred. Total Score: {}", ticket.getTicketNo(), totalScore);

            } else if (event.getOldTotalScore() != null) {
                // Chính sách 4: Rejoin (vào lại)
                totalScore = event.getOldTotalScore() - 30;
                log.info("Ticket {} rejoined. Total Score after penalty: {}", ticket.getTicketNo(), totalScore);

            } else {
                // Chính sách 1: Lấy vé mới
                // Tính S_base
                ApiResponse<CustomerSegmentConfigDto> segResponse = managementClient.getCustomerSegment(ticket.getCustomerSegmentId());
                int sBase = segResponse.getData() != null ? segResponse.getData().getBasePriorityScore() : 0;

                // Tính S_service
                int sService = 0;
                if (ticket.getServiceTypeId() != null) {
                   ApiResponse<ServiceTypeConfigDto> svResponse = managementClient.getServiceType(ticket.getServiceTypeId());
                   sService = svResponse.getData() != null && svResponse.getData().getPriorityWeight() != null
                           ? svResponse.getData().getPriorityWeight() : 0;
                }

                totalScore = sBase + sService;
                log.info("Ticket {} newly created. BaseScore: {}, SvcScore: {} -> Total: {}",
                        ticket.getTicketNo(), sBase, sService, totalScore);
            }

            // Đẩy vé vào Redis chờ phục vụ (lưu ở Dạng Negative/Âm để thứ tự Sắp xếp ZSET ưu tiên cao ngoi lên đầu)
            // Vì ZSET lấy min trước, còn Score ta đang tính theo điểm hệ 100/50 ... Càng to càng VIP. => Nghịch đảo
            queueService.addTicketToQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId(), -totalScore.doubleValue());

            eventPublisher.publishEvent(new TicketQueuedEvent(this, ticket, totalScore.doubleValue()));

        } catch (Exception e) {
            log.error("Failed to process queue ticket for [{}]: {}", ticket.getTicketNo(), e.getMessage());
        }
    }
}
