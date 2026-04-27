package org.example.management.repository;

import org.example.management.entity.CustomerSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, Long> {
    Optional<CustomerSegment> findByCode(String code);
    boolean existsByCode(String code);
}

