package org.example.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.example.management.entity.enums.CounterStatus;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(
    name = "service_counter",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"branch_id", "code"})}
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CounterStatus status;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @ManyToMany
    @JoinTable(
            name = "counter_request_group",
            joinColumns = @JoinColumn(name = "counter_id"),
            inverseJoinColumns = @JoinColumn(name = "request_group_id")
    )
    private Set<RequestGroup> requestGroups;

    @ManyToMany
    @JoinTable(
            name = "counter_customer_segment",
            joinColumns = @JoinColumn(name = "counter_id"),
            inverseJoinColumns = @JoinColumn(name = "customer_segment_id")
    )
    private Set<CustomerSegment> customerSegments;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

