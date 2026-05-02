package org.example.management.repository;

import org.example.management.entity.Reason;
import org.example.management.entity.enums.ReasonType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReasonRepository extends JpaRepository<Reason, Long> {
    boolean existsByCode(String code);
    List<Reason> findByTypeAndIsActiveTrue(ReasonType type);
    List<Reason> findByIsActiveTrue();
}
