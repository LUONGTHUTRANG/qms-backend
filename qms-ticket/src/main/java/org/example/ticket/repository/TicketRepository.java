package org.example.ticket.repository;

import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
}
