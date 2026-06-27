package org.example.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.ticket.client.AuthClient;
import org.example.ticket.client.ManagementClient;
import org.example.ticket.client.dto.CounterSessionDto;
import org.example.ticket.client.dto.CustomerSegmentConfigDto;
import org.example.ticket.client.dto.ReasonConfigDto;
import org.example.ticket.client.dto.RequestGroupDto;
import org.example.ticket.client.dto.ServiceCounterDto;
import org.example.ticket.dto.*;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.TicketEvent;
import org.example.ticket.entity.TicketSequence;
import org.example.ticket.entity.TicketSequenceId;
import org.example.ticket.entity.enums.TicketEventType;
import org.example.ticket.entity.enums.TicketStatus;
import org.example.ticket.event.TicketStatusChangedEvent;
import org.example.ticket.event.TicketRemovedFromQueueEvent;
import org.example.ticket.repository.TicketEventRepository;
import org.example.ticket.repository.TicketRepository;
import org.example.ticket.repository.TicketSequenceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import feign.FeignException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private final EstimatedWaitTimeService estimatedWaitTimeService;
    private final TicketQueueCoordinatorService ticketQueueCoordinatorService;
    private final RedisTemplate<String, Object> redisTemplate;

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

        // Láº¥y counter code náº¿u cÃ³ currentCounterId
        if (entity.getCurrentCounterId() != null) {
            try {
                counterCode = managementClient.getServiceCounter(entity.getCurrentCounterId()).getData().getCode();
            } catch (Exception ignored) {
                // Náº¿u khÃ´ng láº¥y Ä‘Æ°á»£c code, Ä‘á»ƒ null
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
                .trackingCode(entity.getTrackingCode())
                .status(entity.getStatus())
                .rejoinCount(entity.getRejoinCount())
                .skipExpireAt(entity.getSkipExpireAt())
                .carryOverMinutes(entity.getCarryOverMinutes())
                .callAttemptCount(entity.getCallAttemptCount())
                .currentCounterId(entity.getCurrentCounterId())
                .currentCounterCode(counterCode)
                .lastCalledAt(entity.getLastCalledAt())
                .servingAt(entity.getServingAt())
                .doneAt(entity.getDoneAt())
                .cancelledAt(entity.getCancelledAt())
                .createdAt(entity.getCreatedAt())
                .lastQueueEnteredAt(entity.getLastQueueEnteredAt())
                .lastQueueExitedAt(entity.getLastQueueExitedAt())
                .initialEwt(entity.getInitialEwt())
                .build();
    }

    private double calculateCurrentPriorityMinutes(Double queueScore) {
        if (queueScore == null) {
            return 0.0;
        }

        long nowEpochSeconds = LocalDateTime.now()
                .atZone(ZoneId.systemDefault())
                .toEpochSecond();
        return Math.max(0.0, (nowEpochSeconds - queueScore) / 60.0);
    }

    private int calculateMinutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        return Math.max(0, Math.toIntExact(ChronoUnit.MINUTES.between(start, end)));
    }

    private int calculateQueueWaitMinutes(Ticket ticket, LocalDateTime fallbackExitTime) {
        LocalDateTime enteredAt = ticket.getLastQueueEnteredAt() != null
                ? ticket.getLastQueueEnteredAt()
                : ticket.getCreatedAt();
        LocalDateTime exitTime = ticket.getLastQueueExitedAt() != null
                ? ticket.getLastQueueExitedAt()
                : fallbackExitTime;
        return calculateMinutesBetween(enteredAt, exitTime);
    }

    private int getTargetWaitMinutes(Long segmentId) {
        if (segmentId == null) {
            return 20;
        }

        try {
            CustomerSegmentConfigDto segment = managementClient.getCustomerSegment(segmentId).getData();
            if (segment != null && segment.getTargetWaitMinutes() != null && segment.getTargetWaitMinutes() > 0) {
                return segment.getTargetWaitMinutes();
            }
        } catch (Exception ignored) {
        }

        return 20;
    }

    private double getTransferReuseRatio(Long reasonId) {
        if (reasonId == null) {
            return 0.0;
        }

        try {
            ReasonConfigDto reason = managementClient.getReason(reasonId).getData();
            if (reason == null || reason.getCode() == null) {
                return 0.0;
            }

            return switch (reason.getCode()) {
                case "T_WRONG_SERVICE" -> 0.0;
                case "T_ESCALATION" -> 0.5;
                case "T_NEXT_STEP", "T_DEVICE_ERROR" -> 1.0;
                default -> 0.0;
            };
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private int calculateReusableServiceMinutes(Ticket ticket, LocalDateTime now, Long reasonId) {
        if (ticket.getServingAt() == null) {
            return 0;
        }

        int servedMinutes = calculateMinutesBetween(ticket.getServingAt(), now);
        int cappedTargetWait = getTargetWaitMinutes(ticket.getCustomerSegmentId());
        double reuseRatio = getTransferReuseRatio(reasonId);

        return Math.min((int) Math.round(servedMinutes * reuseRatio), cappedTargetWait);
    }

    private int calculateTransferCarryOverMinutes(Ticket ticket, TicketStatus oldStatus, LocalDateTime now, Long reasonId) {
        int existingCarryOverMinutes = ticket.getCarryOverMinutes() != null ? ticket.getCarryOverMinutes() : 0;
        int queueWaitMinutes = switch (oldStatus) {
            case WAITING -> calculateQueueWaitMinutes(ticket, now);
            case CALLED -> calculateQueueWaitMinutes(ticket, ticket.getLastCalledAt());
            case SERVING -> calculateQueueWaitMinutes(ticket, ticket.getLastCalledAt());
            default -> 0;
        };

        if (oldStatus != TicketStatus.SERVING) {
            return existingCarryOverMinutes + queueWaitMinutes;
        }

        return existingCarryOverMinutes + queueWaitMinutes + calculateReusableServiceMinutes(ticket, now, reasonId);
    }

    private int calculateSkipHoldCarryOverMinutes(Ticket ticket, LocalDateTime now) {
        int queueWaitMinutes = calculateQueueWaitMinutes(
                ticket,
                ticket.getLastCalledAt() != null ? ticket.getLastCalledAt() : now
        );
        return Math.max(0, (int) Math.round(queueWaitMinutes * 0.5));
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
        LocalDateTime now = LocalDateTime.now();
        String ticketNo = generateTicketNumber(request.getBranchId(), today, request.getPrefixCode());

        Ticket ticket = Ticket.builder()
                .branchId(request.getBranchId())
                .businessDate(today)
                .ticketNo(ticketNo)
                .requestGroupId(request.getRequestGroupId())
                .serviceTypeId(request.getServiceTypeId())
                .customerSegmentId(request.getCustomerSegmentId())
                .phoneNumber(request.getPhoneNumber())
                .trackingCode(java.util.UUID.randomUUID().toString())
                .status(TicketStatus.WAITING)
                .rejoinCount(0)
                .carryOverMinutes(0)
                .callAttemptCount(0)
                .lastQueueEnteredAt(now)
                .lastQueueExitedAt(null)
                .build();

        ticket = ticketRepository.save(ticket);

        ticketQueueCoordinatorService.enqueueWaitingTicket(ticket);

        Integer initialEwt = estimatedWaitTimeService.calculateEstimatedWaitTime(
                ticket.getBranchId(),
                ticket.getRequestGroupId(),
                ticket.getCustomerSegmentId(),
                ticket.getId()
        );
        ticket.setInitialEwt(initialEwt);
        ticket = ticketRepository.save(ticket);

        TicketEvent event = TicketEvent.builder()
                .ticket(ticket)
                .eventType(TicketEventType.CREATED)
                .toStatus(TicketStatus.WAITING)
                .performedByUserId(userId)
                .build();
        ticketEventRepository.save(event);

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

        // Get current priority minutes from Redis queue or suspend queue
        try {
            Double queueScore = redisQueueService.getTicketScore(ticket.getBranchId(), requestGroupId, segmentId, ticket.getId());
            if (queueScore != null) {
                score = calculateCurrentPriorityMinutes(queueScore);
            } else {
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
        boolean isRecallingTicket = (oldStatus == TicketStatus.CALLED && newStatus == TicketStatus.CALLED);
        LocalDateTime now = LocalDateTime.now();

        if (oldStatus == newStatus && !isRecallingTicket) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket is already in status " + newStatus);
        }

        if (newStatus == TicketStatus.SKIPPED_HOLD) {
            if (oldStatus != TicketStatus.CALLED) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Only CALLED tickets can be moved to SKIPPED_HOLD");
            }
            if (ticket.getRejoinCount() >= 1) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket exceeded the maximum number of rejoin attempts");
            }
        }

        TicketEventType eventType = TicketEventType.TRANSFERRED;
        if (newStatus == TicketStatus.CALLED) {
            eventType = TicketEventType.CALLED;
            ticket.setCallAttemptCount(ticket.getCallAttemptCount() + 1);
            ticket.setLastCalledAt(now);
            if (!isRecallingTicket && ticket.getLastQueueExitedAt() == null) {
                ticket.setLastQueueExitedAt(now);
            }

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
        } else if (newStatus == TicketStatus.SERVING) {
            eventType = TicketEventType.SERVING_STARTED;
            ticket.setServingAt(now);
        } else if (newStatus == TicketStatus.DONE) {
            eventType = TicketEventType.DONE;
            ticket.setDoneAt(now);
        } else if (newStatus == TicketStatus.CANCELLED) {
            eventType = TicketEventType.CANCELLED;
            ticket.setCancelledAt(now);
        } else if (newStatus == TicketStatus.SKIPPED_HOLD) {
            eventType = TicketEventType.SKIPPED_HOLD;
            ticket.setCarryOverMinutes(calculateSkipHoldCarryOverMinutes(ticket, now));
            ticket.setSkipExpireAt(now.plusMinutes(15));
        }

        ticket.setStatus(newStatus);
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

        if (oldStatus == TicketStatus.SKIPPED_HOLD && newStatus != TicketStatus.SKIPPED_HOLD) {
            redisQueueService.removeTicketFromExpireQueue(ticketId);
            redisQueueService.removeTicketFromSuspendQueue(
                    ticket.getBranchId(),
                    ticket.getRequestGroupId(),
                    ticket.getCustomerSegmentId(),
                    ticketId
            );
        }

        if (newStatus == TicketStatus.SKIPPED_HOLD) {
            removedFromQueue = redisQueueService.removeTicketFromQueue(
                    ticket.getBranchId(),
                    ticket.getRequestGroupId(),
                    ticket.getCustomerSegmentId(),
                    ticket.getId()
            );

            redisQueueService.addTicketToSuspendQueue(
                    ticket.getBranchId(),
                    ticket.getRequestGroupId(),
                    ticket.getCustomerSegmentId(),
                    ticket.getId(),
                    ticket.getCarryOverMinutes() != null ? ticket.getCarryOverMinutes() : 0
            );

            long expireTimeMillis = ticket.getSkipExpireAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            redisQueueService.addTicketToExpireQueue(ticket.getId(), expireTimeMillis);
        } else if (newStatus == TicketStatus.CANCELLED || newStatus == TicketStatus.DONE || newStatus == TicketStatus.SERVING) {
            removedFromQueue = redisQueueService.removeTicketFromQueue(
                    ticket.getBranchId(),
                    ticket.getRequestGroupId(),
                    ticket.getCustomerSegmentId(),
                    ticket.getId()
            );
        } else if (newStatus == TicketStatus.CALLED && !isRecallingTicket) {
            removedFromQueue = redisQueueService.removeTicketFromQueue(
                    ticket.getBranchId(),
                    ticket.getRequestGroupId(),
                    ticket.getCustomerSegmentId(),
                    ticket.getId()
            );
        }

        eventPublisher.publishEvent(buildTicketStatusChangedEvent(ticket, oldStatus));
        if (removedFromQueue) {
            eventPublisher.publishEvent(new TicketRemovedFromQueueEvent(this, ticket, newStatus));
        }

        return mapToDto(ticket);
    }


    // ChÃ­nh sÃ¡ch 4: Rejoin
    @Transactional
    public TicketDto rejoinTicket(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));

        if (ticket.getStatus() != TicketStatus.SKIPPED_HOLD) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket must be in SKIPPED_HOLD to rejoin");
        }

        LocalDateTime now = LocalDateTime.now();
        if (ticket.getSkipExpireAt() != null && now.isAfter(ticket.getSkipExpireAt())) {
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.SKIPPED_EXPIRED);
            ticket = ticketRepository.save(ticket);

            redisQueueService.removeTicketFromSuspendQueue(
                    ticket.getBranchId(),
                    ticket.getRequestGroupId(),
                    ticket.getCustomerSegmentId(),
                    ticket.getId()
            );
            redisQueueService.removeTicketFromExpireQueue(ticket.getId());

            TicketEvent expiredEvent = TicketEvent.builder()
                    .ticket(ticket)
                    .eventType(TicketEventType.SKIPPED_EXPIRED)
                    .fromStatus(oldStatus)
                    .toStatus(TicketStatus.SKIPPED_EXPIRED)
                    .performedByUserId(userId)
                    .note("Ticket expired before rejoin")
                    .build();
            ticketEventRepository.save(expiredEvent);
            eventPublisher.publishEvent(buildTicketStatusChangedEvent(ticket, oldStatus));

            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket exceeded skip expiry time");
        }

        if (ticket.getRejoinCount() >= 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket exceeded the maximum number of rejoin attempts");
        }

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.WAITING);
        ticket.setRejoinCount(ticket.getRejoinCount() + 1);
        ticket.setSkipExpireAt(null);
        ticket.setCurrentCounterId(null);
        ticket.setLastQueueEnteredAt(now);
        ticket.setLastQueueExitedAt(null);
        ticket = ticketRepository.save(ticket);

        redisQueueService.removeTicketFromSuspendQueue(
                ticket.getBranchId(),
                ticket.getRequestGroupId(),
                ticket.getCustomerSegmentId(),
                ticket.getId()
        );
        redisQueueService.removeTicketFromExpireQueue(ticket.getId());

        TicketEvent event = TicketEvent.builder()
                .ticket(ticket)
                .eventType(TicketEventType.REJOINED)
                .fromStatus(oldStatus)
                .toStatus(TicketStatus.WAITING)
                .performedByUserId(userId)
                .build();
        ticketEventRepository.save(event);

        ticketQueueCoordinatorService.enqueueWaitingTicket(ticket);
        eventPublisher.publishEvent(buildTicketStatusChangedEvent(ticket, oldStatus));
        return mapToDto(ticket);
    }

    // ChÃ­nh sÃ¡ch 5: Chuyá»ƒn dá»‹ch vá»¥ (Forward Quáº§y)
    @Transactional
    public TicketDto transferTicket(Long ticketId, Long newRequestGroupId, Long newServiceTypeId, Long userId, Long reasonId, String note) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Ticket not found"));

        TicketStatus oldStatus = ticket.getStatus();
        if (oldStatus != TicketStatus.SERVING && oldStatus != TicketStatus.WAITING && oldStatus != TicketStatus.CALLED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ticket in status " + oldStatus + " cannot be transferred");
        }

        LocalDateTime now = LocalDateTime.now();
        Long oldBranchId = ticket.getBranchId();
        Long oldRequestGroupId = ticket.getRequestGroupId();
        Long oldSegmentId = ticket.getCustomerSegmentId();
        Long oldServiceTypeId = ticket.getServiceTypeId();

        int carryOverMinutes = calculateTransferCarryOverMinutes(ticket, oldStatus, now, reasonId);
        if (oldStatus == TicketStatus.WAITING || oldStatus == TicketStatus.CALLED) {
            redisQueueService.removeTicketFromQueue(oldBranchId, oldRequestGroupId, oldSegmentId, ticket.getId());
        }

        ticket.setRequestGroupId(newRequestGroupId);
        ticket.setServiceTypeId(newServiceTypeId);
        ticket.setStatus(TicketStatus.WAITING);
        ticket.setCarryOverMinutes(carryOverMinutes);
        ticket.setCurrentCounterId(null);
        ticket.setSkipExpireAt(null);
        ticket.setLastQueueEnteredAt(now);
        ticket.setLastQueueExitedAt(null);
        ticket = ticketRepository.save(ticket);

        TicketEvent event = TicketEvent.builder()
                .ticket(ticket)
                .eventType(TicketEventType.TRANSFERRED)
                .fromStatus(oldStatus)
                .toStatus(TicketStatus.WAITING)
                .oldRequestGroupId(oldRequestGroupId)
                .newRequestGroupId(newRequestGroupId)
                .oldServiceTypeId(oldServiceTypeId)
                .newServiceTypeId(newServiceTypeId)
                .performedByUserId(userId)
                .reasonId(reasonId)
                .note(note)
                .build();
        ticketEventRepository.save(event);

        ticketQueueCoordinatorService.enqueueWaitingTicket(ticket);
        if (oldStatus == TicketStatus.WAITING || oldStatus == TicketStatus.CALLED) {
            eventPublisher.publishEvent(new TicketRemovedFromQueueEvent(
                    this,
                    ticket,
                    TicketStatus.WAITING,
                    oldBranchId,
                    oldRequestGroupId,
                    oldSegmentId
            ));
        }

        return mapToDto(ticket);
    }

    public List<QueueItemDto> getNextTicketsForCounter(Long userId) {
        // B1: Láº¥y counterId tá»« auth service
        CounterSessionDto session = null;
        try {
            session = authClient.getActiveSession(userId).getData();
        } catch (FeignException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        if (session == null || session.getCounterId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        // B2: Láº¥y branch vÃ  danh sÃ¡ch cÃ¡c requestGroupId mÃ  quáº§y nÃ y cÃ³ thá»ƒ phá»¥c vá»¥
        ServiceCounterDto counter = managementClient.getServiceCounter(session.getCounterId()).getData();
        if (counter == null || counter.getRequestGroupIds() == null || counter.getRequestGroupIds().isEmpty()
            || counter.getCustomerSegmentIds() == null || counter.getCustomerSegmentIds().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Counter is not configured to serve any request groups or customer segments");
        }

        restoreMissingWaitingTicketsForCounter(counter);

        List<QueueItemDto> masterQueue = new java.util.ArrayList<>();
        java.util.Map<Long, String> requestGroupNameCache = new java.util.HashMap<>();

        // B3: MÃ³c dá»¯ liá»‡u tá»« Redis
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
                        Double queueScore = tuple.getScore();

                        masterQueue.add(QueueItemDto.builder()
                                .ticketId(tId)
                                .score(calculateCurrentPriorityMinutes(queueScore))
                                .requestGroupId(rgId)
                                .requestGroupName(rgName)
                                .build());
                    }
                }
            }
        }

        // Náº¿u quáº§y lÃ m nhiá»u nhÃ³m dá»‹ch vá»¥ => Cáº§n há»£p nháº¥t vÃ  sort láº¡i xem ai cao Ä‘iá»ƒm nháº¥t tá»•ng thá»ƒ
        masterQueue.sort((q1, q2) -> Double.compare(q2.getScore(), q1.getScore())); // Sort Descending

        List<QueueItemDto> topTickets = masterQueue.stream()
                .limit(10)
                .collect(Collectors.toList());
        List<Long> topTicketIds = topTickets.stream()
                .map(QueueItemDto::getTicketId)
                .collect(Collectors.toList());
        List<Ticket> dbTickets = ticketRepository.findAllById(topTicketIds);
        java.util.Map<Long, Ticket> ticketMap = dbTickets.stream()
                .collect(Collectors.toMap(Ticket::getId, t -> t));

        java.util.Map<Long, CustomerSegmentConfigDto> segmentCache = new java.util.HashMap<>();
        for (QueueItemDto item : topTickets) {
            Ticket t = ticketMap.get(item.getTicketId());

            if (t != null) {
                item.setTicketNo(t.getTicketNo());
                item.setCreatedAt(t.getCreatedAt());

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
            }
        }
        return topTickets;
    }

    public QueueItemDto getTopTicketForCounter(Long userId) {
        List<QueueItemDto> queue = getNextTicketsForCounter(userId);
        if (queue.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Quáº§y hiá»‡n khÃ´ng cÃ³ phiáº¿u chá» cáº§n phá»¥c vá»¥.");
        }
        return queue.get(0);
    }

    public List<org.example.ticket.dto.SuspendedQueueItemDto> getSuspendedTicketsForCounter(Long userId) {
        // B1: Láº¥y counterId tá»« auth service
        CounterSessionDto session = null;
        try {
            session = authClient.getActiveSession(userId).getData();
        } catch (FeignException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        if (session == null || session.getCounterId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        // B2: Láº¥y branch vÃ  danh sÃ¡ch cÃ¡c requestGroupId mÃ  quáº§y nÃ y cÃ³ thá»ƒ phá»¥c vá»¥
        ServiceCounterDto counter = managementClient.getServiceCounter(session.getCounterId()).getData();
        if (counter == null || counter.getRequestGroupIds() == null || counter.getRequestGroupIds().isEmpty()
            || counter.getCustomerSegmentIds() == null || counter.getCustomerSegmentIds().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Counter is not configured to serve any request groups or customer segments");
        }

        List<org.example.ticket.dto.SuspendedQueueItemDto> masterSuspendQueue = new java.util.ArrayList<>();
        java.util.Map<Long, String> requestGroupNameCache = new java.util.HashMap<>();

        // B3: Láº¥y dá»¯ liá»‡u tá»« Redis suspend queue (Hash structure)
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

        List<org.example.ticket.dto.SuspendedQueueItemDto> topSuspendedTickets = masterSuspendQueue.stream()
                .limit(10)
                .collect(Collectors.toList());

        if (topSuspendedTickets.isEmpty()) {
            return topSuspendedTickets;
        }
        List<Long> ticketIds = topSuspendedTickets.stream()
                .map(org.example.ticket.dto.SuspendedQueueItemDto::getTicketId)
                .collect(Collectors.toList());

        List<Ticket> dbTickets = ticketRepository.findAllById(ticketIds);
        java.util.Map<Long, Ticket> ticketMap = dbTickets.stream()
                .collect(Collectors.toMap(Ticket::getId, t -> t));

        java.util.Map<Long, CustomerSegmentConfigDto> segmentCache = new java.util.HashMap<>();
        for (org.example.ticket.dto.SuspendedQueueItemDto item : topSuspendedTickets) {
            Ticket t = ticketMap.get(item.getTicketId());

            if (t != null) { // Äáº£m báº£o an toÃ n náº¿u dá»¯ liá»‡u Redis vÃ  MariaDB cÃ³ Ä‘á»™ trá»…
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
            }
        }

        return topSuspendedTickets;
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
        ServiceCounterDto counter = null;
        try {
            counter = managementClient.getServiceCounter(session.getCounterId()).getData();
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Failed to retrieve counter information");
        }

        if (counter == null || counter.getRequestGroupIds() == null || counter.getRequestGroupIds().isEmpty()
                || counter.getCustomerSegmentIds() == null || counter.getCustomerSegmentIds().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Counter is not configured to serve any request groups or customer segments");
        }

        restoreMissingWaitingTicketsForCounter(counter);

        List<QueueItemDto> candidates = getTopCandidateTicketsFromRedis(counter, 5);

        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Quáº§y hiá»‡n khÃ´ng cÃ³ phiáº¿u chá» cáº§n phá»¥c vá»¥.");
        }

        QueueItemDto selectedTicket = null;
        for (QueueItemDto candidate : candidates) {
            boolean isRemoved = redisQueueService.removeTicketFromQueue(
                    counter.getBranchId(),
                    candidate.getRequestGroupId(),
                    candidate.getSegmentId(),
                    candidate.getTicketId()
            );

            if (isRemoved) {
                selectedTicket = candidate; // XÃ³a thÃ nh cÃ´ng, vÃ© nÃ y thuá»™c vá» quáº§y hiá»‡n táº¡i
                break;
            }
        }
        if (selectedTicket == null) {
            // Náº¿u khÃ´ng xÃ³a Ä‘Æ°á»£c vÃ© nÃ o, nghÄ©a lÃ  cÃ¡c vÃ© Ä‘Ã£ bá»‹ quáº§y khÃ¡c "há»›t tay trÃªn"
            throw new BusinessException(HttpStatus.CONFLICT, "Há»‡ thá»‘ng báº­n hoáº·c vÃ© Ä‘Ã£ Ä‘Æ°á»£c quáº§y khÃ¡c gá»i, vui lÃ²ng báº¥m gá»i láº¡i.");
        }
        return updateStatusWithCounter(selectedTicket.getTicketId(), TicketStatus.CALLED, userId, session.getCounterId(), null, null);
    }
    private List<QueueItemDto> getTopCandidateTicketsFromRedis(ServiceCounterDto counter, int limit) {
        List<QueueItemDto> masterQueue = new java.util.ArrayList<>();

        // Chá»‰ láº·p vÃ  láº¥y dá»¯ liá»‡u thÃ´ tá»« Redis
        for (Long rgId : counter.getRequestGroupIds()) {
            for (Long segmentId : counter.getCustomerSegmentIds()) {
                Set<ZSetOperations.TypedTuple<Object>> rgQueue = redisQueueService.getTicketsInQueue(counter.getBranchId(), rgId, segmentId);

                if (rgQueue != null) {
                    for (ZSetOperations.TypedTuple<Object> tuple : rgQueue) {
                        String tIdStr = (String) tuple.getValue();
                        Long tId = Long.valueOf(tIdStr);
                        Double queueScore = tuple.getScore();

                        // Khá»Ÿi táº¡o DTO thÃ´, bá» qua viá»‡c query DB láº¥y tÃªn tuá»•i
                        masterQueue.add(QueueItemDto.builder()
                                .ticketId(tId)
                                .score(calculateCurrentPriorityMinutes(queueScore))
                                .requestGroupId(rgId)
                                .segmentId(segmentId) // Quan trá»ng Ä‘á»ƒ lÃ¡t ná»¯a truyá»n vÃ o hÃ m xÃ³a Redis
                                .build());
                    }
                }
            }
        }

        // Sáº¯p xáº¿p vÃ  cáº¯t top limit
        masterQueue.sort((q1, q2) -> Double.compare(q2.getScore(), q1.getScore()));

        return masterQueue.stream().limit(limit).collect(Collectors.toList());
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

    private void restoreMissingWaitingTicketsForCounter(ServiceCounterDto counter) {
        List<Ticket> waitingTickets = ticketRepository.findByBranchIdAndBusinessDateAndStatus(
                counter.getBranchId(),
                LocalDate.now(),
                TicketStatus.WAITING
        );

        if (waitingTickets == null || waitingTickets.isEmpty()) {
            return;
        }

        Set<Long> allowedRequestGroupIds = counter.getRequestGroupIds();
        Set<Long> allowedCustomerSegmentIds = counter.getCustomerSegmentIds();

        for (Ticket waitingTicket : waitingTickets) {
            Long requestGroupId = waitingTicket.getRequestGroupId();
            Long segmentId = waitingTicket.getCustomerSegmentId();

            if (requestGroupId == null || segmentId == null) {
                continue;
            }

            if (!allowedRequestGroupIds.contains(requestGroupId) || !allowedCustomerSegmentIds.contains(segmentId)) {
                continue;
            }

            Double queueScore = redisQueueService.getTicketScore(
                    waitingTicket.getBranchId(),
                    requestGroupId,
                    segmentId,
                    waitingTicket.getId()
            );

            if (queueScore == null) {
                ticketQueueCoordinatorService.enqueueWaitingTicket(waitingTicket);
            }
        }
    }

    public SessionInfoDto getSessionInfo(Long userId) {
        // B1: Láº¥y active session cá»§a userId
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

        // B2: Äáº¿m sá»‘ ticket WAITING trong hÃ ng Ä‘á»£i
        List<QueueItemDto> waitingTickets = getNextTicketsForCounter(userId);
        int waitingCount = waitingTickets != null ? waitingTickets.size() : 0;

        // B3: Láº¥y danh sÃ¡ch ticket DONE thuá»™c phiÃªn hiá»‡n táº¡i (currentCounterId = counterId)
        List<Ticket> completedTickets = ticketRepository.findByCurrentCounterIdAndStatusAndBusinessDate(
                counterId,
                TicketStatus.DONE,
                today
        );

        int completedCount = completedTickets.size();

        // B4: TÃ­nh tá»•ng thá»i gian phiÃªn
        long sessionDurationSeconds = 0;
        LocalDateTime now = LocalDateTime.now();

        // B4.1: TÃ­nh thá»i gian cho ticket DONE (tá»« lastCalledAt â†’ doneAt)
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

        // B4.2: TÃ­nh thá»i gian cho ticket SERVING vÃ  CALLED (tá»« lastCalledAt â†’ hiá»‡n táº¡i)
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

    // Láº¥y danh sÃ¡ch vÃ© hiá»‡n táº¡i cho nhiá»u quáº§y (tá»‘i Æ°u hÃ³a - 1 query thay vÃ¬ gá»i láº§n lÆ°á»£t)
    public Map<Long, TicketDto> getTicketsForCounters(List<Long> counterIds) {
        if (counterIds == null || counterIds.isEmpty()) {
            return new java.util.HashMap<>();
        }

        // Láº¥y vÃ© CALLED hoáº·c SERVING cho cÃ¡c quáº§y nÃ y
        List<Ticket> tickets = ticketRepository.findByCurrentCounterIdsAndStatuses(
                counterIds,
                java.util.Arrays.asList(TicketStatus.CALLED, TicketStatus.SERVING)
        );

        // Táº¡o map: counterId -> TicketDto (láº¥y ticket má»›i nháº¥t cho má»—i quáº§y)
        Map<Long, TicketDto> counterTicketMap = new java.util.HashMap<>();
        for (Ticket ticket : tickets) {
            // Náº¿u quáº§y chÆ°a cÃ³ vÃ©, hoáº·c vÃ© má»›i nÃ y Ä‘Æ°á»£c cáº­p nháº­t gáº§n Ä‘Ã¢y hÆ¡n, thÃ¬ cáº­p nháº­t
            if (!counterTicketMap.containsKey(ticket.getCurrentCounterId()) ||
                    ticket.getUpdatedAt() != null &&
                    counterTicketMap.get(ticket.getCurrentCounterId()).getId() != null) {
                counterTicketMap.put(ticket.getCurrentCounterId(), mapToDto(ticket));
            }
        }

        return counterTicketMap;
    }

    // Láº¥y danh sÃ¡ch vÃ© hiá»‡n táº¡i cho nhiá»u quáº§y dÆ°á»›i dáº¡ng Map<Long, Map<String, Object>> (Ä‘á»ƒ tá»‘i Æ°u hÃ³a gá»i tá»« qms-management)
    public Map<Long, Map<String, Object>> getTicketsForCountersAsMap(List<Long> counterIds) {
        Map<Long, TicketDto> ticketDtoMap = getTicketsForCounters(counterIds);

        Map<Long, Map<String, Object>> result = new java.util.HashMap<>();
        ticketDtoMap.forEach((counterId, ticketDto) -> {
            if (ticketDto != null) {
                Map<String, Object> ticketMap = new java.util.HashMap<>();
                // Chá»‰ láº¥y 3 fields cáº§n thiáº¿t
                ticketMap.put("id", ticketDto.getId());
                ticketMap.put("ticketNo", ticketDto.getTicketNo());
                ticketMap.put("status", ticketDto.getStatus());

                result.put(counterId, ticketMap);
            }
        });

        return result;
    }

    /**
     * Láº¥y danh sÃ¡ch ticket theo tráº¡ng thÃ¡i, cÃ³ thá»ƒ filter theo counterId
     * Order theo lastCalledAt (gáº§n nháº¥t á»Ÿ Ä‘áº§u)
     * Cache counter code Ä‘á»ƒ tá»‘i Æ°u
     */
    public List<TicketDto> getTicketsByStatusAndCounter(TicketStatus status, Long counterId) {
        List<Ticket> tickets;

        if (counterId != null) {
            // Filter theo counterId
            tickets = ticketRepository.findByStatusAndCurrentCounterIdOrderByLastCalledAtDesc(status, counterId);
        } else {
            // Láº¥y full khÃ´ng filter
            tickets = ticketRepository.findByStatusOrderByLastCalledAtDesc(status);
        }

        // Cache Ä‘á»ƒ láº¥y code má»™t láº§n
        java.util.Map<Long, String> counterCodeCache = new java.util.HashMap<>();

        return tickets.stream()
                .map(ticket -> {
                    String counterCode = null;
                    if (ticket.getCurrentCounterId() != null) {
                        // Check cache trÆ°á»›c
                        if (counterCodeCache.containsKey(ticket.getCurrentCounterId())) {
                            counterCode = counterCodeCache.get(ticket.getCurrentCounterId());
                        } else {
                            // Gá»i API láº¥y code
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
                            .trackingCode(ticket.getTrackingCode())
                            .status(ticket.getStatus())
                            .rejoinCount(ticket.getRejoinCount())
                            .skipExpireAt(ticket.getSkipExpireAt())
                            .carryOverMinutes(ticket.getCarryOverMinutes())
                            .callAttemptCount(ticket.getCallAttemptCount())
                            .currentCounterId(ticket.getCurrentCounterId())
                            .currentCounterCode(counterCode)
                            .lastCalledAt(ticket.getLastCalledAt())
                            .servingAt(ticket.getServingAt())
                            .doneAt(ticket.getDoneAt())
                            .cancelledAt(ticket.getCancelledAt())
                            .createdAt(ticket.getCreatedAt())
                            .lastQueueEnteredAt(ticket.getLastQueueEnteredAt())
                            .lastQueueExitedAt(ticket.getLastQueueExitedAt())
                            .initialEwt(ticket.getInitialEwt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Láº¥y danh sÃ¡ch vÃ© Ä‘Ã£ bá»‹ há»§y (CANCELLED status) trong phiÃªn lÃ m viá»‡c hiá»‡n táº¡i cá»§a user
     */
    public List<TicketDto> getCancelledTicketsForCounter(Long userId) {
        CounterSessionDto session = null;
        try {
            session = authClient.getActiveSession(userId).getData();
        } catch (FeignException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        if (session == null || session.getCounterId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User does not have an active counter session");
        }

        LocalDate today = LocalDate.now();
        List<Ticket> tickets = ticketRepository.findByCurrentCounterIdAndStatusAndBusinessDate(
                session.getCounterId(),
                TicketStatus.CANCELLED,
                today
        );
        
        // Sort theo cancelledAt descending (má»›i nháº¥t á»Ÿ Ä‘áº§u)
        tickets.sort((t1, t2) -> {
            if (t1.getCancelledAt() == null || t2.getCancelledAt() == null) {
                return 0;
            }
            return t2.getCancelledAt().compareTo(t1.getCancelledAt());
        });

        java.util.Map<Long, String> counterCodeCache = new java.util.HashMap<>();

        return tickets.stream()
                .map(ticket -> {
                    String counterCode = null;
                    if (ticket.getCurrentCounterId() != null) {
                        // Check cache trÆ°á»›c
                        if (counterCodeCache.containsKey(ticket.getCurrentCounterId())) {
                            counterCode = counterCodeCache.get(ticket.getCurrentCounterId());
                        } else {
                            // Gá»i API láº¥y code
                            try {
                                counterCode = managementClient.getServiceCounter(ticket.getCurrentCounterId()).getData().getCode();
                                counterCodeCache.put(ticket.getCurrentCounterId(), counterCode);
                            } catch (Exception ignored) {
                                counterCodeCache.put(ticket.getCurrentCounterId(), null);
                            }
                        }
                    }

                    return TicketDto.builder()
                            .id(ticket.getId())
                            .branchId(ticket.getBranchId())
                            .businessDate(ticket.getBusinessDate())
                            .ticketNo(ticket.getTicketNo())
                            .requestGroupId(ticket.getRequestGroupId())
                            .serviceTypeId(ticket.getServiceTypeId())
                            .customerSegmentId(ticket.getCustomerSegmentId())
                            .phoneNumber(ticket.getPhoneNumber())
                            .trackingCode(ticket.getTrackingCode())
                            .status(ticket.getStatus())
                            .rejoinCount(ticket.getRejoinCount())
                            .skipExpireAt(ticket.getSkipExpireAt())
                            .carryOverMinutes(ticket.getCarryOverMinutes())
                            .callAttemptCount(ticket.getCallAttemptCount())
                            .currentCounterId(ticket.getCurrentCounterId())
                            .currentCounterCode(counterCode)
                            .lastCalledAt(ticket.getLastCalledAt())
                            .servingAt(ticket.getServingAt())
                            .doneAt(ticket.getDoneAt())
                            .cancelledAt(ticket.getCancelledAt())
                            .createdAt(ticket.getCreatedAt())
                            .lastQueueEnteredAt(ticket.getLastQueueEnteredAt())
                            .lastQueueExitedAt(ticket.getLastQueueExitedAt())
                            .initialEwt(ticket.getInitialEwt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public Integer calculateEstimatedWaitTime(Long branchId, Long requestGroupId, Long segmentId, Long ticketId) {
        // 1. Láº¥y vá»‹ trÃ­ N cá»§a vÃ© trong hÃ ng Ä‘á»£i (ZREVRANK)
        Integer delegatedEstimatedWaitTime = estimatedWaitTimeService.calculateEstimatedWaitTime(
                branchId,
                requestGroupId,
                segmentId,
                ticketId
        );
        Long rank = delegatedEstimatedWaitTime != null ? delegatedEstimatedWaitTime.longValue() : 0L;
        
        // Náº¿u vÃ© chÆ°a cÃ³ trong queue (hoáº·c Ä‘Ã£ bá»‹ láº¥y ra), máº·c Ä‘á»‹nh N = 0
        long n = (rank != null) ? rank : 0; 

        // 2. Láº¥y EMA tá»« Redis
        String emaKey = "1";
        Object emaObj = "1";
        String emaStr = (emaObj != null) ? emaObj.toString() : null;
        double ema;

        if (emaStr != null) {
            ema = Double.parseDouble(emaStr);
        } else {
            // Fallback: Láº¥y giÃ¡ trá»‹ SLA máº·c Ä‘á»‹nh tá»« qms-management náº¿u Redis rá»—ng
            try {
                RequestGroupDto groupDto = managementClient.getRequestGroup(requestGroupId).getData();
                ema = (groupDto != null && groupDto.getDefaultServingTime() != null) 
                      ? groupDto.getDefaultServingTime() 
                      : 300.0; // Dá»± phÃ²ng an toÃ n cuá»‘i cÃ¹ng lÃ  5 phÃºt
                
                // LÆ°u ngay vÃ o Redis Ä‘á»ƒ cache cho cÃ¡c lÆ°á»£t sau
                redisTemplate.opsForValue().set(emaKey, String.valueOf(ema));
            } catch (Exception e) {
                ema = 300.0; 
            }
        }

        // 3. Láº¥y sá»‘ lÆ°á»£ng quáº§y C Ä‘ang Active phá»¥c vá»¥ nhÃ³m nÃ y
        // LÆ°u Ã½: Báº¡n cáº§n viáº¿t má»™t hÃ m gá»i sang qms-auth/qms-management Ä‘á»ƒ Ä‘áº¿m sá»‘ session active
        int activeCounters = 1;
        int c = Math.max(1, activeCounters); // RÃ o cháº¯n chá»‘ng káº¹t sá»‘ chia 0

        // 4. TÃ­nh toÃ¡n EWT
        double ewt = (n * ema) / c;
        return (int) Math.round(ewt);
    }

    // Láº¥y sá»‘ quáº§y active phá»¥c vá»¥ nhÃ³m dá»‹ch vá»¥ vÃ  phÃ¢n khÃºc nÃ y
    private int getActiveCounters(Long branchId, Long requestGroupId, Long segmentId) {
        try {
            // Láº¥y danh sÃ¡ch ID quáº§y Ä‘ang active tá»« Auth Service
            java.util.List<Long> activeCounterIds = authClient.getActiveCounterIds().getData();
            if (activeCounterIds == null || activeCounterIds.isEmpty()) {
                return 0;
            }

            // Láº¥y danh sÃ¡ch quáº§y theo chi nhÃ¡nh tá»« Management Service
            java.util.List<ServiceCounterDto> countersInBranch = managementClient.getCountersByBranchId(branchId).getData();
            if (countersInBranch == null || countersInBranch.isEmpty()) {
                return 0;
            }

            int activeCount = 0;
            for (ServiceCounterDto counter : countersInBranch) {
                // Kiá»ƒm tra xem quáº§y nÃ y cÃ³ Ä‘ang active khÃ´ng
                if (activeCounterIds.contains(counter.getId())) {
                    // Kiá»ƒm tra xem quáº§y nÃ y cÃ³ phá»¥c vá»¥ requestGroupId vÃ  segmentId khÃ´ng
                    if (counter.getRequestGroupIds() != null && counter.getRequestGroupIds().contains(requestGroupId)
                        && counter.getCustomerSegmentIds() != null && counter.getCustomerSegmentIds().contains(segmentId)) {
                        activeCount++;
                    }
                }
            }
            return activeCount;
        } catch (Exception e) {
            return 0;
        }
    }
    public TicketTrackingDto getTicketTrackingInfo(String trackingCode) {
        Ticket ticket = ticketRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new RuntimeException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin vÃ©"));

        // Náº¿u vÃ© Ä‘Ã£ qua lÆ°á»£t hoáº·c hoÃ n thÃ nh, khÃ´ng cáº§n tÃ­nh EWT ná»¯a
        if (ticket.getStatus() != TicketStatus.WAITING) {
            return TicketTrackingDto.builder()
                    .ticketNo(ticket.getTicketNo())
                    .status(ticket.getStatus())
                    // Gá»i thÃªm Feign láº¥y tÃªn quáº§y náº¿u status lÃ  SERVING
                    .build();
        }

        // 1. TÃ­nh sá»‘ ngÆ°á»i xáº¿p trÆ°á»›c (N)
        WaitEstimateDto waitEstimate = estimatedWaitTimeService.estimate(
                ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId(), ticket.getId()
        );
        Long rank = (long) waitEstimate.peopleAhead();
        int n = (rank != null) ? rank.intValue() : 0;

        // 2. TÃ­nh sá»‘ quáº§y Ä‘ang phá»¥c vá»¥ (C)
        int activeCounters = waitEstimate.activeCounters();

        // 3. Gá»i láº¡i hÃ m tÃ­nh EWT (Ä‘Ã£ vÃ¡ lá»—i á»Ÿ trÃªn)
        Integer dynamicEwt = waitEstimate.estimatedWaitTime();

        return TicketTrackingDto.builder()
                .ticketNo(ticket.getTicketNo())
                .status(ticket.getStatus())
                .peopleAhead(n)
                .activeCounters(activeCounters) // Tráº£ vá» sá»‘ thá»±c táº¿ (cÃ³ thá»ƒ lÃ  0 náº¿u nhÃ¢n viÃªn Ä‘i vá»‡ sinh háº¿t)
                .estimatedWaitTime(dynamicEwt)
                .serviceAvailable(waitEstimate.serviceAvailable())
                .build();
    }
}



