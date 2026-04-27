package org.example.management.repository;

import org.example.management.entity.ServiceCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceCounterRepository extends JpaRepository<ServiceCounter, Long> {
    boolean existsByBranchIdAndCode(Long branchId, String code);
    List<ServiceCounter> findByBranchId(Long branchId);
}

