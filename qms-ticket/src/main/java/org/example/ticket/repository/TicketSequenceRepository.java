package org.example.ticket.repository;

import org.example.ticket.entity.TicketSequence;
import org.example.ticket.entity.TicketSequenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketSequenceRepository extends JpaRepository<TicketSequence, TicketSequenceId> {
}

