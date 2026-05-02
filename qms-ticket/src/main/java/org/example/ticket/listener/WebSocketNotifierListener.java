package org.example.ticket.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.enums.TicketStatus;
import org.example.ticket.event.TicketQueuedEvent;
import org.example.ticket.event.TicketStatusChangedEvent;
import org.example.ticket.event.TicketRemovedFromQueueEvent;
import org.example.ticket.client.ManagementClient;
import org.example.ticket.client.dto.CustomerSegmentConfigDto;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotifierListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ManagementClient managementClient;

    @Async
    @EventListener
    public void notifyBranchAboutTicket(TicketQueuedEvent event) {
        Ticket ticket = event.getTicket();
        String channel = "qms:events:branch:" + ticket.getBranchId() + ":" + ticket.getRequestGroupId() + ":" + (ticket.getCustomerSegmentId() != null ? ticket.getCustomerSegmentId() : "0");

        try {
            String requestGroupName = "Unknown";
            try {
                requestGroupName = managementClient.getRequestGroup(ticket.getRequestGroupId()).getData().getName();
            } catch (Exception ignored) { }

            CustomerSegmentConfigDto segment = null;
            if (ticket.getCustomerSegmentId() != null) {
                try {
                    segment = managementClient.getCustomerSegment(ticket.getCustomerSegmentId()).getData();
                } catch (Exception ignored) { }
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "NEW_TICKET");
            payload.put("ticketId", ticket.getId());
            payload.put("ticketNo", ticket.getTicketNo());
            payload.put("requestGroupId", ticket.getRequestGroupId());
            payload.put("requestGroupName", requestGroupName);
            if (segment != null) {
                payload.put("segmentId", segment.getId());
                payload.put("segmentCode", segment.getCode());
                payload.put("segmentName", segment.getName());
            }
            payload.put("status", ticket.getStatus().name());
            payload.put("score", event.getScore());

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(channel, json);

            log.info("Broadcasted NEW_TICKET {} to channel {}", ticket.getTicketNo(), channel);
        } catch (Exception e) {
            log.error("Error broadcasting via Redis Pub/Sub: {}", e.getMessage());
        }
    }

    @Async
    @EventListener
    public void notifyBranchAboutStatusChange(TicketStatusChangedEvent event) {
        Ticket ticket = event.getTicket();
        TicketStatus newStatus = ticket.getStatus();
        TicketStatus oldStatus = event.getOldStatus();

        String channel = "qms:events:branch:" + ticket.getBranchId() + ":" + ticket.getRequestGroupId() + ":" + (ticket.getCustomerSegmentId() != null ? ticket.getCustomerSegmentId() : "0");

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "TICKET_STATUS_CHANGED");
            payload.put("ticketId", ticket.getId());
            payload.put("oldStatus", oldStatus.name());
            payload.put("newStatus", newStatus.name());

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(channel, json);

            log.info("Broadcasted status change for ticket {} from {} to {} on channel {}", 
                    ticket.getTicketNo(), oldStatus, newStatus, channel);
        } catch (Exception e) {
            log.error("Error broadcasting status change via Redis Pub/Sub: {}", e.getMessage());
        }
    }

    @Async
    @EventListener
    public void notifyBranchAboutTicketRemovedFromQueue(TicketRemovedFromQueueEvent event) {
        Ticket ticket = event.getTicket();
        TicketStatus newStatus = event.getNewStatus();

        String channel = "qms:events:branch:" + ticket.getBranchId() + ":" + ticket.getRequestGroupId() + ":" + (ticket.getCustomerSegmentId() != null ? ticket.getCustomerSegmentId() : "0");

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "TICKET_REMOVED_FROM_QUEUE");
            payload.put("ticketId", ticket.getId());
            payload.put("newStatus", newStatus.name());

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(channel, json);

            log.info("Broadcasted ticket {} removed from queue with status {} on channel {}", 
                    ticket.getTicketNo(), newStatus, channel);
        } catch (Exception e) {
            log.error("Error broadcasting queue removal via Redis Pub/Sub: {}", e.getMessage());
        }
    }
}
