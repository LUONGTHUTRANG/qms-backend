package org.example.auth.repository;

import org.example.auth.entity.CounterSession;
import org.example.auth.entity.enums.CounterSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CounterSessionRepository extends JpaRepository<CounterSession, Long> {
    Optional<CounterSession> findByUserIdAndStatus(Long userId, CounterSessionStatus status);
    List<CounterSession> findByCounterIdAndStatus(Long counterId, CounterSessionStatus status);
}

