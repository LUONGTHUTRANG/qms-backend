package org.example.ticket.repository;

import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByBranchIdAndBusinessDate(Long branchId, LocalDate businessDate);
    List<Ticket> findByBranchIdAndBusinessDateAndStatus(Long branchId, LocalDate businessDate, TicketStatus status);
    Optional<Ticket> findByBranchIdAndBusinessDateAndTicketNo(Long branchId, LocalDate businessDate, String ticketNo);
    Optional<Ticket> findFirstByCurrentCounterIdAndStatusInOrderByUpdatedAtDesc(Long currentCounterId, List<TicketStatus> statuses);
    List<Ticket> findByCurrentCounterIdAndStatusAndBusinessDate(Long currentCounterId, TicketStatus status, LocalDate businessDate);
    List<Ticket> findByCurrentCounterIdAndStatusInAndBusinessDate(Long currentCounterId, List<TicketStatus> statuses, LocalDate businessDate);
    
    @Query("SELECT t FROM Ticket t WHERE t.currentCounterId IN :counterIds AND t.status IN :statuses")
    List<Ticket> findByCurrentCounterIdsAndStatuses(
            @Param("counterIds") List<Long> counterIds,
            @Param("statuses") List<TicketStatus> statuses
    );

    @Query("SELECT t FROM Ticket t WHERE t.currentCounterId IN :counterIds AND t.status IN :statuses AND t.businessDate = :businessDate")
    List<Ticket> findByCurrentCounterIdsAndStatusesAndBusinessDate(
            @Param("counterIds") List<Long> counterIds,
            @Param("statuses") List<TicketStatus> statuses,
            @Param("businessDate") LocalDate businessDate
    );

    @Query("SELECT t FROM Ticket t WHERE t.status = :status ORDER BY t.lastCalledAt DESC NULLS LAST")
    List<Ticket> findByStatusOrderByLastCalledAtDesc(@Param("status") TicketStatus status);

    @Query("SELECT t FROM Ticket t WHERE t.status = :status AND t.currentCounterId = :counterId ORDER BY t.lastCalledAt DESC NULLS LAST")
    List<Ticket> findByStatusAndCurrentCounterIdOrderByLastCalledAtDesc(
            @Param("status") TicketStatus status,
            @Param("counterId") Long counterId
    );

    @Query("SELECT t FROM Ticket t WHERE t.status = 'SKIPPED_HOLD' AND t.skipExpireAt IS NOT NULL AND t.skipExpireAt <= :now")
    List<Ticket> findExpiredSkipHoldTickets(@Param("now") LocalDateTime now);

    Optional<Ticket> findByTrackingCode(String trackingCode);
}
