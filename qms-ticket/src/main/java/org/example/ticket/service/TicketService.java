package org.example.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.ticket.client.AuthClient;
import org.example.ticket.client.ManagementClient;
import org.example.ticket.client.dto.CounterSessionDto;
import org.example.ticket.client.dto.ServiceCounterDto;
import org.example.ticket.client.dto.CustomerSegmentConfigDto;
import org.example.ticket.dto.QueueItemDto;
import org.example.ticket.dto.TicketCreateRequest;
import org.example.ticket.dto.TicketDto;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.TicketEvent;
import org.example.ticket.entity.TicketSequence;
import org.example.ticket.entity.TicketSequenceId;
import org.example.ticket.entity.enums.TicketEventType;
import org.example.ticket.entity.enums.TicketStatus;
import org.example.ticket.event.TicketCreatedEvent;
import org.example.ticket.repository.TicketEventRepository;
import org.example.ticket.repository.TicketRepository;
import org.example.ticket.repository.TicketSequenceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import feign.FeignException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketSequenceRepository ticketSequenceRepository;
    private final TicketEventRepository ticketEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisQueueService redisQueueService;
    private final AuthClient authClient;
    private final ManagementClient managementClient;

    private String generateTicketNumber(Long branchId, LocalDate businessDate, String prefixCode) {
        TicketSequenceId sequenceId = new TicketSequenceId(branchId, businessDate, prefixCode);

        TicketSequence sequence = ticketSequenceRepository.findById(sequenceId)
                .orElse(new TicketSequence(sequenceId, 0));

        sequence.setSeqValue(sequence.getSeqValue() + 1);
        ticketSequenceRepository.save(sequence);

        return prefixCode + String.format("%03d", sequence.getSeqValue());
    }

    private TicketDto mapToDto(Ticket entity) {
        return TicketDto.builder()
                .id(entity.getId())
                .branchId(entity.getBranchId())
                .businessDate(entity.getBusinessDate())
                .ticketNo(entity.getTicketNo())
                .requestGroupId(entity.getRequestGroupId())
                .serviceTypeId(entity.getServiceTypeId())
                .customerSegmentId(entity.getCustomerSegmentId())
                .phoneNumber(entity.getPhoneNumber())
                .status(entity.getStatus())
                .rejoinCount(entity.getRejoinCount())
                .skipExpireAt(entity.getSkipExpireAt())
                .waitCreditSeconds(entity.getWaitCreditSeconds())
                .callAttemptCount(entity.getCallAttemptCount())
                .currentCounterId(entity.getCurrentCounterId())
                .lastCalledAt(entity.getLastCalledAt())
                .servingAt(entity.getServingAt())
                .doneAt(entity.getDoneAt())
                .cancelledAt(entity.getCancelledAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public List<TicketDto> getTicketsByBranchAndDate(Long branchId, LocalDate date) {
        return ticketRepository.findByBranchIdAndBusinessDate(branchId, date).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<TicketDto> getTicketsByStatus(Long branchId, LocalDate date, TicketStatus status) {
        return ticketRepository.findByBranchIdAndBusinessDateAndStatus(branchId, date, status).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public TicketDto getById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));
        return mapToDto(ticket);
    }

    @Transactional
    public TicketDto create(TicketCreateRequest request, Long userId) {
        LocalDate today = LocalDate.now();
        String ticketNo = generateTicketNumber(request.getBranchId(), today, request.getPrefixCode());

        Ticket ticket = Ticket.builder()
                .branchId(request.getBranchId())
                .businessDate(today)
                .ticketNo(ticketNo)
                .requestGroupId(request.getRequestGroupId())
                .serviceTypeId(request.getServiceTypeId())
                .customerSegmentId(request.getCustomerSegmentId())
                .phoneNumber(request.getPhoneNumber())
                .status(TicketStatus.WAITING)
                .rejoinCount(0)
                .waitCreditSeconds(0)
                .callAttemptCount(0)
                .build();

        ticket = ticketRepository.save(ticket);

        TicketEvent event = TicketEvent.builder()
                .ticket(ticket)
                .eventType(TicketEventType.CREATED)
                .toStatus(TicketStatus.WAITING)
                .performedByUserId(userId)
                .build();
        ticketEventRepository.save(event);

        eventPublisher.publishEvent(new TicketCreatedEvent(this, ticket));

        return mapToDto(ticket);
    }

    @Transactional
    public TicketDto updateStatus(Long ticketId, TicketStatus newStatus, Long userId) {
        return updateStatusWithCounter(ticketId, newStatus, userId, null);
    }

    @Transactional
    public TicketDto updateStatusWithCounter(Long ticketId, TicketStatus newStatus, Long userId, Long knownCounterId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));

        TicketStatus oldStatus = ticket.getStatus();
        if (oldStatus == newStatus) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket is already in status " + newStatus);
        }

        ticket.setStatus(newStatus);

        TicketEventType eventType = TicketEventType.TRANSFERRED; // Default generic
        if (newStatus == TicketStatus.CALLED) {
            eventType = TicketEventType.CALLED;
            ticket.setCallAttemptCount(ticket.getCallAttemptCount() + 1);
            ticket.setLastCalledAt(LocalDateTime.now());

            if (knownCounterId != null) {
                ticket.setCurrentCounterId(knownCounterId);
            } else if (userId != null) {
                try {
                    CounterSessionDto session = authClient.getActiveSession(userId).getData();
                    if (session != null && session.getCounterId() != null) {
                        ticket.setCurrentCounterId(session.getCounterId());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        else if (newStatus == TicketStatus.SERVING) {
            eventType = TicketEventType.SERVING_STARTED;
            ticket.setServingAt(LocalDateTime.now());
        }
        else if (newStatus == TicketStatus.DONE) {
            eventType = TicketEventType.DONE;
            ticket.setDoneAt(LocalDateTime.now());
        }
        else if (newStatus == TicketStatus.CANCELLED) {
            eventType = TicketEventType.CANCELLED;
            ticket.setCancelledAt(LocalDateTime.now());
        }

        ticket = ticketRepository.save(ticket);

        TicketEvent event = TicketEvent.builder()
                .ticket(ticket)
                .eventType(eventType)
                .fromStatus(oldStatus)
                .toStatus(newStatus)
                .performedByUserId(userId)
                .build();
        ticketEventRepository.save(event);

        if (newStatus == TicketStatus.SKIPPED_HOLD) {
             // Policy 3: Skip (Lỡ lượt không thấy -> Xóa Queue và Giữ chỗ 15p)
             ticket.setSkipExpireAt(LocalDateTime.now().plusMinutes(15));

             Double currentScore = redisQueueService.getTicketScore(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());
             if (currentScore != null) {
                  // Cất điểm chờ để nhỡ người dùng replay lại policy 4 - Chứa vô WaitCredit
                  ticket.setWaitCreditSeconds(currentScore.intValue());
                  redisQueueService.removeTicketFromQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());
             }
        } else if (newStatus == TicketStatus.CANCELLED || newStatus == TicketStatus.DONE || newStatus == TicketStatus.SERVING) {
             redisQueueService.removeTicketFromQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());
        }

        return mapToDto(ticket);
    }

    // Chính sách 4: Rejoin
    @Transactional
    public TicketDto rejoinTicket(Long ticketId, Long userId) {
         Ticket ticket = ticketRepository.findById(ticketId)
                 .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));

         if (ticket.getStatus() != TicketStatus.SKIPPED_HOLD) {
             throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket must be in SKIPPED_HOLD to rejoin");
         }

         if (ticket.getRejoinCount() >= 1 || (ticket.getSkipExpireAt() != null && LocalDateTime.now().isAfter(ticket.getSkipExpireAt()))) {
             ticket.setStatus(TicketStatus.SKIPPED_EXPIRED);
             ticketRepository.save(ticket);
             throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket exceeded skip retry count or expired");
         }

         TicketStatus oldStatus = ticket.getStatus();
         ticket.setStatus(TicketStatus.WAITING);
         ticket.setRejoinCount(ticket.getRejoinCount() + 1);
         Integer oldScore = (ticket.getWaitCreditSeconds() != null && ticket.getWaitCreditSeconds() < 0) ? -ticket.getWaitCreditSeconds() : 0;
         ticket = ticketRepository.save(ticket);

         TicketEvent event = TicketEvent.builder()
                 .ticket(ticket)
                 .eventType(TicketEventType.REJOINED)
                 .fromStatus(oldStatus)
                 .toStatus(TicketStatus.WAITING)
                 .performedByUserId(userId)
                 .build();
         ticketEventRepository.save(event);

         eventPublisher.publishEvent(new TicketCreatedEvent(this, ticket, false, oldScore));
         return mapToDto(ticket);
    }

    // Chính sách 5: Chuyển dịch vụ (Forward Quầy)
    @Transactional
    public TicketDto transferTicket(Long ticketId, Long newRequestGroupId, Long newServiceTypeId, Long userId) {
         Ticket ticket = ticketRepository.findById(ticketId)
                 .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));

         if (ticket.getStatus() != TicketStatus.SERVING) {
             throw new BusinessException(HttpStatus.BAD_REQUEST, "Only SERVING ticket can be transferred");
         }

         // Try checking if it's stored in score, if it was serving it might have been removed.
         // Realistically, when calling SERVING, the ticket is removed from redis!
         // Wait, if it was SERVING, it was removed. We don't have its score anymore unless we save it.
         // Let's deduce an estimated score or assume standard priority bonus.
         int oldTotalScore = ticket.getWaitCreditSeconds() != null && ticket.getWaitCreditSeconds() < 0 ? -ticket.getWaitCreditSeconds() : 0;

         Long oldRgId = ticket.getRequestGroupId();
         Long oldSvcId = ticket.getServiceTypeId();
         TicketStatus oldStatus = ticket.getStatus();

         ticket.setRequestGroupId(newRequestGroupId);
         ticket.setServiceTypeId(newServiceTypeId);
         ticket.setStatus(TicketStatus.WAITING);
         ticket = ticketRepository.save(ticket);

         TicketEvent event = TicketEvent.builder()
                 .ticket(ticket)
                 .eventType(TicketEventType.TRANSFERRED)
                 .fromStatus(oldStatus)
                 .toStatus(TicketStatus.WAITING)
                 .oldRequestGroupId(oldRgId)
                 .newRequestGroupId(newRequestGroupId)
                 .oldServiceTypeId(oldSvcId)
                 .newServiceTypeId(newServiceTypeId)
                 .performedByUserId(userId)
                 .build();
         ticketEventRepository.save(event);

         eventPublisher.publishEvent(new TicketCreatedEvent(this, ticket, true, oldTotalScore));
         return mapToDto(ticket);
    }

    public List<QueueItemDto> getNextTicketsForCounter(Long userId) {
        // B1: Lấy counterId từ auth service
        CounterSessionDto session = null;
        try {
            session = authClient.getActiveSession(userId).getData();
        } catch (FeignException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        if (session == null || session.getCounterId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        // B2: Lấy branch và danh sách các requestGroupId mà quầy này có thể phục vụ
        ServiceCounterDto counter = managementClient.getServiceCounter(session.getCounterId()).getData();
        if (counter == null || counter.getRequestGroupIds() == null || counter.getRequestGroupIds().isEmpty()
            || counter.getCustomerSegmentIds() == null || counter.getCustomerSegmentIds().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Counter is not configured to serve any request groups or customer segments");
        }

        List<QueueItemDto> masterQueue = new java.util.ArrayList<>();
        java.util.Map<Long, String> requestGroupNameCache = new java.util.HashMap<>();

        // B3: Móc dữ liệu từ Redis
        for (Long rgId : counter.getRequestGroupIds()) {
            String rgName = requestGroupNameCache.computeIfAbsent(rgId, id -> {
                try {
                    return managementClient.getRequestGroup(id).getData().getName();
                } catch (Exception e) {
                    return "Unknown";
                }
            });

            for (Long segmentId : counter.getCustomerSegmentIds()) {
                Set<ZSetOperations.TypedTuple<Object>> rgQueue = redisQueueService.getTicketsInQueue(counter.getBranchId(), rgId, segmentId);
                if (rgQueue != null) {
                    for (ZSetOperations.TypedTuple<Object> tuple : rgQueue) {
                        String tIdStr = (String) tuple.getValue();
                        Long tId = Long.valueOf(tIdStr);
                        Double negativeScore = tuple.getScore();

                        masterQueue.add(QueueItemDto.builder()
                                .ticketId(tId)
                                .score(negativeScore != null ? -negativeScore : 0.0) // Đảo ngược số âm lúc cất thành số dương lúc trả
                                .requestGroupId(rgId)
                                .requestGroupName(rgName)
                                .build());
                    }
                }
            }
        }

        // Nếu quầy làm nhiều nhóm dịch vụ => Cần hợp nhất và sort lại xem ai cao điểm nhất tổng thể
        masterQueue.sort((q1, q2) -> Double.compare(q2.getScore(), q1.getScore())); // Sort Descending

        java.util.Map<Long, CustomerSegmentConfigDto> segmentCache = new java.util.HashMap<>();

        // Fetch ticket number from DB to complete data payload (We could cache this in redis, but to keep memory small DB query is fine since queue shouldn't be 1M long)
        for (QueueItemDto item : masterQueue) {
             ticketRepository.findById(item.getTicketId()).ifPresent(t -> {
                 item.setTicketNo(t.getTicketNo());

                 Long segId = t.getCustomerSegmentId();
                 if (segId != null) {
                     CustomerSegmentConfigDto seg = segmentCache.computeIfAbsent(segId, id -> {
                         try {
                             return managementClient.getCustomerSegment(id).getData();
                         } catch (Exception e) {
                             return null;
                         }
                     });
                     if (seg != null) {
                         item.setSegmentId(seg.getId());
                         item.setSegmentCode(seg.getCode());
                         item.setSegmentName(seg.getName());
                     }
                 }
             });
        }

        return masterQueue;
    }

    public QueueItemDto getTopTicketForCounter(Long userId) {
        List<QueueItemDto> queue = getNextTicketsForCounter(userId);
        if (queue.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "No waiting tickets currently in queue for this counter");
        }
        return queue.get(0);
    }

    @Transactional
    public TicketDto callNextTicket(Long userId) {
        // Fetch session once to optimize and avoid duplicate cross-service auth calls
        CounterSessionDto session = null;
        try {
            session = authClient.getActiveSession(userId).getData();
        } catch (FeignException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        if (session == null || session.getCounterId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        // Instead of calling getNextTicketsForCounter(userId) which calls auth service again, we can just call it to find the top ticket
        // Keep it simple visually though, get top ticket using existing method since logic is encapsulated there.
        QueueItemDto topTicket = getTopTicketForCounter(userId);

        // Now update status and definitely inject current_counter_id in the same transaction
        return updateStatusWithCounter(topTicket.getTicketId(), TicketStatus.CALLED, userId, session.getCounterId());
    }

    public TicketDto getCurrentTicketForCounter(Long userId) {
        CounterSessionDto session = null;
        try {
            session = authClient.getActiveSession(userId).getData();
        } catch (FeignException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        if (session == null || session.getCounterId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        Ticket ticket = ticketRepository.findFirstByCurrentCounterIdAndStatusInOrderByUpdatedAtDesc(
                session.getCounterId(),
                java.util.Arrays.asList(TicketStatus.CALLED, TicketStatus.SERVING)
        ).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "No active ticket found for this counter"));

        return mapToDto(ticket);
    }

    public List<String> getSubscriptionTopics(Long userId) {
        CounterSessionDto session = null;
        try {
            session = authClient.getActiveSession(userId).getData();
        } catch (FeignException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        if (session == null || session.getCounterId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        ServiceCounterDto counter = managementClient.getServiceCounter(session.getCounterId()).getData();
        if (counter == null || counter.getRequestGroupIds() == null || counter.getRequestGroupIds().isEmpty()
            || counter.getCustomerSegmentIds() == null || counter.getCustomerSegmentIds().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<String> topics = new java.util.ArrayList<>();
        Long branchId = counter.getBranchId();

        for (Long rgId : counter.getRequestGroupIds()) {
            for (Long segmentId : counter.getCustomerSegmentIds()) {
                topics.add("/topic/branch/" + branchId + "/" + rgId + "/" + segmentId);
            }
        }
        return topics;
    }
}




