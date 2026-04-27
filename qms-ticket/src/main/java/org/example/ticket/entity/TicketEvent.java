package org.example.ticket.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.example.ticket.entity.enums.TicketEventType;
import org.example.ticket.entity.enums.TicketStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private TicketEventType eventType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "performed_by_user_id")
    private Long performedByUserId;

    @Column(name = "counter_id")
    private Long counterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    private TicketStatus toStatus;

    @Column(name = "old_request_group_id")
    private Long oldRequestGroupId;

    @Column(name = "new_request_group_id")
    private Long newRequestGroupId;

    @Column(name = "old_service_type_id")
    private Long oldServiceTypeId;

    @Column(name = "new_service_type_id")
    private Long newServiceTypeId;

    @Column(length = 255)
    private String note;
}

