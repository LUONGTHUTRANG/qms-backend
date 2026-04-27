package org.example.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ticket_sequence")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSequence {

    @EmbeddedId
    private TicketSequenceId id;

    @Column(name = "seq_value", nullable = false)
    private Integer seqValue;
}

