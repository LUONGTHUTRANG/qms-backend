package org.example.ticket.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.enums.TicketStatus;
import org.example.ticket.repository.TicketRepository;
import org.example.ticket.service.RedisQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class EndOfDayCleanupJob {

    private final RedisQueueService queueService;

    @PersistenceContext
    private final EntityManager entityManager;

    // Chính sách 6: Dọn dẹp cuối ca 18:00 -> 0 phút 0 giờ 18 giờ * tháng * ngày mỗI NĂM
    @Scheduled(cron = "0 0 18 * * *")
    @Transactional
    public void cleanupEndOfDay() {
        LocalDate today = LocalDate.now();

        Query updateQ = entityManager.createQuery(
                "UPDATE Ticket t SET t.status = :sts WHERE t.businessDate = :d AND t.status IN (:w, :sh)");
        
        updateQ.setParameter("sts", TicketStatus.CANCELLED);
        updateQ.setParameter("d", today);
        updateQ.setParameter("w", TicketStatus.WAITING);
        updateQ.setParameter("sh", TicketStatus.SKIPPED_HOLD);
        
        int rowCount = updateQ.executeUpdate();
        
        // Clear Redis
        queueService.clearAllQueues();

        log.info("End of Day Cleanup Executed: {} forgotten tickets cancelled. Redis wiped.", rowCount);
    }
}

