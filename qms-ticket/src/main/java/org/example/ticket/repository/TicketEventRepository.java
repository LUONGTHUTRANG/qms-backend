package org.example.ticket.repository;

import org.example.ticket.entity.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {
    List<TicketEvent> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}

