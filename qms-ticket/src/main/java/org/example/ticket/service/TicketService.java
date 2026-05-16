package org.example.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.ticket.client.AuthClient;
import org.example.ticket.client.ManagementClient;
import org.example.ticket.client.dto.CounterSessionDto;
import org.example.ticket.client.dto.ServiceCounterDto;
import org.example.ticket.client.dto.CustomerSegmentConfigDto;
import org.example.ticket.dto.QueueItemDto;
import org.example.ticket.dto.SuspendedQueueItemDto;
import org.example.ticket.dto.TicketCreateRequest;
import org.example.ticket.dto.TicketDto;
import org.example.ticket.dto.SessionInfoDto;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.TicketEvent;
import org.example.ticket.entity.TicketSequence;
import org.example.ticket.entity.TicketSequenceId;
import org.example.ticket.entity.enums.TicketEventType;
import org.example.ticket.entity.enums.TicketStatus;
import org.example.ticket.event.TicketCreatedEvent;
import org.example.ticket.event.TicketStatusChangedEvent;
import org.example.ticket.event.TicketRemovedFromQueueEvent;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
        String counterCode = null;

        // Lấy counter code nếu có currentCounterId
        if (entity.getCurrentCounterId() != null) {
            try {
                counterCode = managementClient.getServiceCounter(entity.getCurrentCounterId()).getData().getCode();
            } catch (Exception ignored) {
                // Nếu không lấy được code, để null
            }
        }

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
                .currentCounterCode(counterCode)
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

    // Helper method to build queue item data for TicketStatusChangedEvent
    private TicketStatusChangedEvent buildTicketStatusChangedEvent(Ticket ticket, TicketStatus oldStatus) {
        Long requestGroupId = ticket.getRequestGroupId();
        String requestGroupName = "Unknown";
        Long segmentId = ticket.getCustomerSegmentId();
        String segmentCode = null;
        String segmentName = null;
        Double score = null;

        try {
            // Get request group name
            requestGroupName = managementClient.getRequestGroup(requestGroupId).getData().getName();
        } catch (Exception ignored) {
        }

        // Get score from Redis queue or suspend queue
        try {
            score = redisQueueService.getTicketScore(ticket.getBranchId(), requestGroupId, segmentId, ticket.getId());
            if (score == null) {
                // Try suspend queue
                score = redisQueueService.getTicketScoreInSuspendQueue(ticket.getBranchId(), requestGroupId, segmentId, ticket.getId());
            }
        } catch (Exception ignored) {
        }

        // Get segment info
        if (segmentId != null) {
            try {
                CustomerSegmentConfigDto segment = managementClient.getCustomerSegment(segmentId).getData();
                if (segment != null) {
                    segmentCode = segment.getCode();
                    segmentName = segment.getName();
                    segmentId = segment.getId();
                }
            } catch (Exception ignored) {
            }
        }

        // Build SuspendedQueueItemDto with complete ticket queue information
        SuspendedQueueItemDto queueItemData = SuspendedQueueItemDto.builder()
                .ticketId(ticket.getId())
                .ticketNo(ticket.getTicketNo())
                .score(score)
                .requestGroupId(requestGroupId)
                .requestGroupName(requestGroupName)
                .segmentId(segmentId)
                .segmentCode(segmentCode)
                .segmentName(segmentName)
                .skipExpireAt(ticket.getSkipExpireAt())
                .rejoinCount(ticket.getRejoinCount())
                .build();

        return new TicketStatusChangedEvent(this, ticket, oldStatus, queueItemData);
    }

    @Transactional
    public TicketDto updateStatus(Long ticketId, TicketStatus newStatus, Long userId, Long reasonId, String note) {
        return updateStatusWithCounter(ticketId, newStatus, userId, null, reasonId, note);
    }

    @Transactional
    public TicketDto updateStatusWithCounter(Long ticketId, TicketStatus newStatus, Long userId, Long knownCounterId) {
        return updateStatusWithCounter(ticketId, newStatus, userId, knownCounterId, null, null);
    }

    @Transactional
    public TicketDto updateStatusWithCounter(Long ticketId, TicketStatus newStatus, Long userId, Long knownCounterId, Long reasonId, String note) {
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
                .reasonId(reasonId)
                .note(note)
                .build();
        ticketEventRepository.save(event);

        boolean removedFromQueue = false;

        if (newStatus == TicketStatus.SKIPPED_HOLD) {
            if (ticket.getRejoinCount() >= 1) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Vé không thể được tạm hoãn do đã vượt quá số lần tạm hoãn quy định.");
            }
             // Policy 3: Skip (Lỡ lượt không thấy -> Xóa Queue và Giữ chỗ 15p)
             ticket.setSkipExpireAt(LocalDateTime.now().plusMinutes(15));

             Double currentScore = redisQueueService.getTicketScore(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());
             
             if (currentScore != null) {
                  // Vé vẫn nằm trong queue - lưu điểm và xóa khỏi queue
                  ticket.setWaitCreditSeconds(currentScore.intValue());
                  redisQueueService.removeTicketFromQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());
             } else {
                  // Vé đã không nằm trong queue (ví dụ: từ CALLED hoặc SERVING) - tính toán score mới
                  // Sử dụng waitCreditSeconds nếu đã tồn tại, hoặc tính từ thời gian chờ đợi
                  if (ticket.getWaitCreditSeconds() != null && ticket.getWaitCreditSeconds() != 0) {
                       currentScore = (double) Math.abs(ticket.getWaitCreditSeconds());
                  } else {
                       // Nếu không có waitCreditSeconds, tính dựa trên thời gian chờ (từ lúc tạo đến giờ)
                       long waitTimeInSeconds = java.time.temporal.ChronoUnit.SECONDS.between(ticket.getCreatedAt(), LocalDateTime.now());
                       currentScore = -(double) waitTimeInSeconds; // Lưu ở dạng âm giống queue
                  }
             }
             
             // Luôn thêm vé vào suspend queue (dù vé có từ queue hay không)
             if (currentScore != null) {
                  redisQueueService.addTicketToSuspendQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId(), currentScore);
                  removedFromQueue = true;
             }
        } else if (newStatus == TicketStatus.CANCELLED || newStatus == TicketStatus.DONE || newStatus == TicketStatus.SERVING) {
             redisQueueService.removeTicketFromQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());
             removedFromQueue = true;
        } else if (newStatus == TicketStatus.CALLED) {
             // Khi được gọi, loại bỏ khỏi queue (vé đã được gọi)
             redisQueueService.removeTicketFromQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());
             removedFromQueue = true;
        }

        eventPublisher.publishEvent(buildTicketStatusChangedEvent(ticket, oldStatus));

        // if (removedFromQueue) {
        //     eventPublisher.publishEvent(new TicketRemovedFromQueueEvent(this, ticket, newStatus));
        // }

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
             
             // Xóa khỏi suspend queue nếu đã hết hạn
             redisQueueService.removeTicketFromSuspendQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());
             
             ticketRepository.save(ticket);
             throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket exceeded skip retry count or expired");
         }

         TicketStatus oldStatus = ticket.getStatus();
         ticket.setStatus(TicketStatus.WAITING);
         ticket.setRejoinCount(ticket.getRejoinCount() + 1);
         Integer oldScore = (ticket.getWaitCreditSeconds() != null && ticket.getWaitCreditSeconds() < 0) ? -ticket.getWaitCreditSeconds() : 0;
         ticket = ticketRepository.save(ticket);
         
         // Xóa khỏi suspend queue
         redisQueueService.removeTicketFromSuspendQueue(ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId());

         TicketEvent event = TicketEvent.builder()
                 .ticket(ticket)
                 .eventType(TicketEventType.REJOINED)
                 .fromStatus(oldStatus)
                 .toStatus(TicketStatus.WAITING)
                 .performedByUserId(userId)
                 .build();
         ticketEventRepository.save(event);

         // Publish TicketStatusChangedEvent to notify status change from SKIPPED_HOLD to WAITING
         eventPublisher.publishEvent(buildTicketStatusChangedEvent(ticket, oldStatus));
        eventPublisher.publishEvent(new TicketCreatedEvent(this, ticket, false, oldScore));
         return mapToDto(ticket);
    }

    // Chính sách 5: Chuyển dịch vụ (Forward Quầy)
    @Transactional
    public TicketDto transferTicket(Long ticketId, Long newRequestGroupId, Long newServiceTypeId, Long userId, Long reasonId, String note) {
         Ticket ticket = ticketRepository.findById(ticketId)
                 .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));

         TicketStatus oldStatus = ticket.getStatus();
         if (oldStatus != TicketStatus.SERVING && oldStatus != TicketStatus.WAITING && oldStatus != TicketStatus.CALLED) {
             throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket in status " + oldStatus + " cannot be transferred");
         }

         Long oldRgId = ticket.getRequestGroupId();
         Long oldSvcId = ticket.getServiceTypeId();

         // Lấy điểm số cũ của ticket để xác định score
         Double currentScore = redisQueueService.getTicketScore(ticket.getBranchId(), oldRgId, ticket.getCustomerSegmentId(), ticket.getId());
         int oldTotalScore = currentScore != null ? currentScore.intValue() : 
                 (ticket.getWaitCreditSeconds() != null && ticket.getWaitCreditSeconds() < 0 ? -ticket.getWaitCreditSeconds() : 0);

         // Xóa vé khỏi hàng đợi hiện tại (nếu nó đang nằm trong đó, vd: WAITING, CALLED)
         if (oldStatus == TicketStatus.WAITING || oldStatus == TicketStatus.CALLED) {
             redisQueueService.removeTicketFromQueue(ticket.getBranchId(), oldRgId, ticket.getCustomerSegmentId(), ticket.getId());
         }

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
                 .reasonId(reasonId)
                 .note(note)
                 .build();
         ticketEventRepository.save(event);

         // Nếu đang từ SERVING chuyển đi, cộng thêm bằng cách set cờ hoặc báo true
         boolean addTransferBonus = (oldStatus == TicketStatus.SERVING);
         eventPublisher.publishEvent(new TicketCreatedEvent(this, ticket, true, addTransferBonus, oldTotalScore));
         
         // Publish event khi remove khỏi queue (transfer từ WAITING hoặc CALLED)
         if (oldStatus == TicketStatus.WAITING || oldStatus == TicketStatus.CALLED) {
             eventPublisher.publishEvent(new TicketRemovedFromQueueEvent(this, ticket, TicketStatus.WAITING));
         }
         
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

    public List<org.example.ticket.dto.SuspendedQueueItemDto> getSuspendedTicketsForCounter(Long userId) {
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

        List<org.example.ticket.dto.SuspendedQueueItemDto> masterSuspendQueue = new java.util.ArrayList<>();
        java.util.Map<Long, String> requestGroupNameCache = new java.util.HashMap<>();

        // B3: Lấy dữ liệu từ Redis suspend queue (Hash structure)
        for (Long rgId : counter.getRequestGroupIds()) {
            String rgName = requestGroupNameCache.computeIfAbsent(rgId, id -> {
                try {
                    return managementClient.getRequestGroup(id).getData().getName();
                } catch (Exception e) {
                    return "Unknown";
                }
            });

            for (Long segmentId : counter.getCustomerSegmentIds()) {
                Map<Object, Object> suspendQueueHash = redisQueueService.getTicketsInSuspendQueue(counter.getBranchId(), rgId, segmentId);
                if (suspendQueueHash != null && !suspendQueueHash.isEmpty()) {
                    for (Map.Entry<Object, Object> entry : suspendQueueHash.entrySet()) {
                        String tIdStr = entry.getKey().toString();
                        Double score = 0.0;
                        try {
                            score = Double.parseDouble(entry.getValue().toString());
                        } catch (NumberFormatException e) {
                            score = 0.0;
                        }

                        masterSuspendQueue.add(org.example.ticket.dto.SuspendedQueueItemDto.builder()
                                .ticketId(Long.valueOf(tIdStr))
                                .score(score)
                                .requestGroupId(rgId)
                                .requestGroupName(rgName)
                                .build());
                    }
                }
            }
        }

        // Sort theo score descending
        masterSuspendQueue.sort((q1, q2) -> Double.compare(q2.getScore(), q1.getScore()));

        java.util.Map<Long, CustomerSegmentConfigDto> segmentCache = new java.util.HashMap<>();

        // Lấy thêm thông tin từ DB (ticketNo, segment, skipExpireAt, rejoinCount)
        for (org.example.ticket.dto.SuspendedQueueItemDto item : masterSuspendQueue) {
             ticketRepository.findById(item.getTicketId()).ifPresent(t -> {
                 item.setTicketNo(t.getTicketNo());
                 item.setSkipExpireAt(t.getSkipExpireAt());
                 item.setRejoinCount(t.getRejoinCount());

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

        return masterSuspendQueue;
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

    public SessionInfoDto getSessionInfo(Long userId) {
        // B1: Lấy active session của userId
        CounterSessionDto session = null;
        try {
            session = authClient.getActiveSession(userId).getData();
        } catch (FeignException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        if (session == null || session.getCounterId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        Long counterId = session.getCounterId();
        LocalDate today = LocalDate.now();

        // B2: Đếm số ticket WAITING trong hàng đợi
        List<QueueItemDto> waitingTickets = getNextTicketsForCounter(userId);
        int waitingCount = waitingTickets != null ? waitingTickets.size() : 0;

        // B3: Lấy danh sách ticket DONE thuộc phiên hiện tại (currentCounterId = counterId)
        List<Ticket> completedTickets = ticketRepository.findByCurrentCounterIdAndStatusAndBusinessDate(
                counterId,
                TicketStatus.DONE,
                today
        );

        int completedCount = completedTickets.size();

        // B4: Tính tổng thời gian phiên
        long sessionDurationSeconds = 0;
        LocalDateTime now = LocalDateTime.now();

        // B4.1: Tính thời gian cho ticket DONE (từ lastCalledAt → doneAt)
        if (completedTickets != null && !completedTickets.isEmpty()) {
            for (Ticket ticket : completedTickets) {
                if (ticket.getLastCalledAt() != null && ticket.getDoneAt() != null) {
                    long seconds = java.time.temporal.ChronoUnit.SECONDS.between(
                            ticket.getLastCalledAt(),
                            ticket.getDoneAt()
                    );
                    sessionDurationSeconds += seconds;
                }
            }
        }

        // B4.2: Tính thời gian cho ticket SERVING và CALLED (từ lastCalledAt → hiện tại)
        List<Ticket> ongoingTickets = ticketRepository.findByCurrentCounterIdAndStatusInAndBusinessDate(
                counterId,
                java.util.Arrays.asList(TicketStatus.SERVING, TicketStatus.CALLED),
                today
        );

        if (ongoingTickets != null && !ongoingTickets.isEmpty()) {
            for (Ticket ticket : ongoingTickets) {
                if (ticket.getLastCalledAt() != null) {
                    long seconds = java.time.temporal.ChronoUnit.SECONDS.between(
                            ticket.getLastCalledAt(),
                            now
                    );
                    sessionDurationSeconds += seconds;
                }
            }
        }

        // B5: Build response
        return SessionInfoDto.builder()
                .counterId(counterId)
                .userId(userId)
                .waitingCount(waitingCount)
                .completedCount(completedCount)
                .sessionDurationSeconds(sessionDurationSeconds)
                .build();
    }

    // Lấy danh sách vé hiện tại cho nhiều quầy (tối ưu hóa - 1 query thay vì gọi lần lượt)
    public Map<Long, TicketDto> getTicketsForCounters(List<Long> counterIds) {
        if (counterIds == null || counterIds.isEmpty()) {
            return new java.util.HashMap<>();
        }

        // Lấy vé CALLED hoặc SERVING cho các quầy này
        List<Ticket> tickets = ticketRepository.findByCurrentCounterIdsAndStatuses(
                counterIds,
                java.util.Arrays.asList(TicketStatus.CALLED, TicketStatus.SERVING)
        );

        // Tạo map: counterId -> TicketDto (lấy ticket mới nhất cho mỗi quầy)
        Map<Long, TicketDto> counterTicketMap = new java.util.HashMap<>();
        for (Ticket ticket : tickets) {
            // Nếu quầy chưa có vé, hoặc vé mới này được cập nhật gần đây hơn, thì cập nhật
            if (!counterTicketMap.containsKey(ticket.getCurrentCounterId()) ||
                    ticket.getUpdatedAt() != null &&
                    counterTicketMap.get(ticket.getCurrentCounterId()).getId() != null) {
                counterTicketMap.put(ticket.getCurrentCounterId(), mapToDto(ticket));
            }
        }

        return counterTicketMap;
    }

    // Lấy danh sách vé hiện tại cho nhiều quầy dưới dạng Map<Long, Map<String, Object>> (để tối ưu hóa gọi từ qms-management)
    public Map<Long, Map<String, Object>> getTicketsForCountersAsMap(List<Long> counterIds) {
        Map<Long, TicketDto> ticketDtoMap = getTicketsForCounters(counterIds);

        Map<Long, Map<String, Object>> result = new java.util.HashMap<>();
        ticketDtoMap.forEach((counterId, ticketDto) -> {
            if (ticketDto != null) {
                Map<String, Object> ticketMap = new java.util.HashMap<>();
                // Chỉ lấy 3 fields cần thiết
                ticketMap.put("id", ticketDto.getId());
                ticketMap.put("ticketNo", ticketDto.getTicketNo());
                ticketMap.put("status", ticketDto.getStatus());

                result.put(counterId, ticketMap);
            }
        });

        return result;
    }

    /**
     * Lấy danh sách ticket theo trạng thái, có thể filter theo counterId
     * Order theo lastCalledAt (gần nhất ở đầu)
     * Cache counter code để tối ưu
     */
    public List<TicketDto> getTicketsByStatusAndCounter(TicketStatus status, Long counterId) {
        List<Ticket> tickets;

        if (counterId != null) {
            // Filter theo counterId
            tickets = ticketRepository.findByStatusAndCurrentCounterIdOrderByLastCalledAtDesc(status, counterId);
        } else {
            // Lấy full không filter
            tickets = ticketRepository.findByStatusOrderByLastCalledAtDesc(status);
        }

        // Cache để lấy code một lần
        java.util.Map<Long, String> counterCodeCache = new java.util.HashMap<>();

        return tickets.stream()
                .map(ticket -> {
                    String counterCode = null;
                    if (ticket.getCurrentCounterId() != null) {
                        // Check cache trước
                        if (counterCodeCache.containsKey(ticket.getCurrentCounterId())) {
                            counterCode = counterCodeCache.get(ticket.getCurrentCounterId());
                        } else {
                            // Gọi API lấy code
                            try {
                                counterCode = managementClient.getServiceCounter(ticket.getCurrentCounterId()).getData().getCode();
                                counterCodeCache.put(ticket.getCurrentCounterId(), counterCode);
                            } catch (Exception ignored) {
                                counterCodeCache.put(ticket.getCurrentCounterId(), null);
                            }
                        }
                    }

                    // Build DTO with cached counter code
                    return TicketDto.builder()
                            .id(ticket.getId())
                            .branchId(ticket.getBranchId())
                            .businessDate(ticket.getBusinessDate())
                            .ticketNo(ticket.getTicketNo())
                            .requestGroupId(ticket.getRequestGroupId())
                            .serviceTypeId(ticket.getServiceTypeId())
                            .customerSegmentId(ticket.getCustomerSegmentId())
                            .phoneNumber(ticket.getPhoneNumber())
                            .status(ticket.getStatus())
                            .rejoinCount(ticket.getRejoinCount())
                            .skipExpireAt(ticket.getSkipExpireAt())
                            .waitCreditSeconds(ticket.getWaitCreditSeconds())
                            .callAttemptCount(ticket.getCallAttemptCount())
                            .currentCounterId(ticket.getCurrentCounterId())
                            .currentCounterCode(counterCode)
                            .lastCalledAt(ticket.getLastCalledAt())
                            .servingAt(ticket.getServingAt())
                            .doneAt(ticket.getDoneAt())
                            .cancelledAt(ticket.getCancelledAt())
                            .createdAt(ticket.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
