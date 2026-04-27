package org.example.ticket.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.example.ticket.entity.enums.TicketStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "ticket",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"branch_id", "business_date", "ticket_no"})}
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "ticket_no", nullable = false, length = 10)
    private String ticketNo;

    @Column(name = "request_group_id", nullable = false)
    private Long requestGroupId;

    @Column(name = "service_type_id")
    private Long serviceTypeId;

    @Column(name = "customer_segment_id", nullable = false)
    private Long customerSegmentId;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "rejoin_count", nullable = false)
    private Integer rejoinCount;

    @Column(name = "skip_expire_at")
    private LocalDateTime skipExpireAt;

    @Column(name = "wait_credit_seconds", nullable = false)
    private Integer waitCreditSeconds;

    @Column(name = "call_attempt_count", nullable = false)
    private Integer callAttemptCount;

    @Column(name = "current_counter_id")
    private Long currentCounterId;

    @Column(name = "last_called_at")
    private LocalDateTime lastCalledAt;

    @Column(name = "serving_at")
    private LocalDateTime servingAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

