package org.example.management.repository;

import org.example.management.entity.PriorityCustomerMock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PriorityCustomerMockRepository extends JpaRepository<PriorityCustomerMock, Long> {
    Optional<PriorityCustomerMock> findByPhoneNumberAndCustomerSegmentIdAndIsActiveTrue(String phoneNumber, Long customerSegmentId);
}

