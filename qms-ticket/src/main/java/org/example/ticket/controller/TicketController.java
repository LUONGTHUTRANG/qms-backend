package org.example.ticket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.example.ticket.dto.*;
import org.example.ticket.entity.enums.TicketStatus;
import org.example.ticket.service.TicketService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.example.ticket.context.UserContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ticket/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService service;

    @GetMapping
    public ApiResponse<List<TicketDto>> getTickets(
            @RequestParam("branchId") Long branchId,
            @RequestParam(value = "status", required = false) TicketStatus status,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate queryDate = date != null ? date : LocalDate.now();
        if (status != null) {
            return ApiResponse.success(service.getTicketsByStatus(branchId, queryDate, status));
        }
        return ApiResponse.success(service.getTicketsByBranchAndDate(branchId, queryDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TicketDto> createTicket(
            @RequestHeader(value = "X-Auth-User-Id", required = false) Long userId,
            @Valid @RequestBody TicketCreateRequest request) {
        return ApiResponse.success(service.create(request, userId), "Ticket generated successfully");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<TicketDto> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody org.example.ticket.dto.TicketStatusUpdateRequest request) {
        Long userId = UserContextHolder.getUserId();
        if (request.getStatus() == TicketStatus.CALLED) {
            return ApiResponse.success(service.updateStatusWithCounter(id, request.getStatus(), userId, null, request.getReasonId(), request.getReason()), "Ticket status updated");
        }
        return ApiResponse.success(service.updateStatus(id, request.getStatus(), userId, request.getReasonId(), request.getReason()), "Ticket status updated");
    }

    @PostMapping("/{id}/rejoin")
    public ApiResponse<TicketDto> rejoinTicket(
            @PathVariable("id") Long id) {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.rejoinTicket(id, userId));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<TicketDto> transferTicket(
            @PathVariable("id") Long id,
            @RequestBody TicketTransferRequest request) {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.transferTicket(id, request.getNewRequestGroupId(), request.getNewServiceTypeId(), userId, request.getReasonId(), request.getReason()));
    }

    @GetMapping("/next-in-queue")
    public ApiResponse<List<QueueItemDto>> getNextTicketsForCounter() {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.getNextTicketsForCounter(userId));
    }

    @GetMapping("/suspended-tickets")
    public ApiResponse<List<org.example.ticket.dto.SuspendedQueueItemDto>> getSuspendedTicketsForCounter() {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.getSuspendedTicketsForCounter(userId));
    }

    @GetMapping("/top-in-queue")
    public ApiResponse<QueueItemDto> getTopTicketForCounter() {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.getTopTicketForCounter(userId));
    }

    @PostMapping("/call-next")
    public ApiResponse<TicketDto> callNextTicket() {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.callNextTicket(userId), "Successfully called the top priority ticket");
    }

    @GetMapping("/current")
    public ApiResponse<TicketDto> getCurrentTicket() {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.getCurrentTicketForCounter(userId));
    }

    @GetMapping("/topics")
    public ApiResponse<List<String>> getCounterSubscriptionTopics() {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.getSubscriptionTopics(userId));
    }

    @GetMapping("/session-info")
    public ApiResponse<SessionInfoDto> getSessionInfo() {
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(service.getSessionInfo(userId), "Session information retrieved successfully");
    }

    @GetMapping("/counters-current")
    public ApiResponse<Map<Long, Map<String, Object>>> getTicketsForCounters(
            @RequestParam("counterIds") List<Long> counterIds) {
        return ApiResponse.success(service.getTicketsForCountersAsMap(counterIds));
    }

    @PostMapping("/list-by-status")
    public ApiResponse<List<TicketDto>> getTicketsByStatusAndCounter(
            @Valid @RequestBody TicketListRequest request) {
        return ApiResponse.success(service.getTicketsByStatusAndCounter(request.getStatus(), request.getCounterId()));
    }

    @GetMapping("/cancelled-tickets")
    public ApiResponse<List<TicketDto>> getCancelledTickets(
            @RequestParam("branchId") Long branchId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        return ApiResponse.success(service.getCancelledTickets(branchId, queryDate), "Cancelled tickets retrieved successfully");
    }
}
