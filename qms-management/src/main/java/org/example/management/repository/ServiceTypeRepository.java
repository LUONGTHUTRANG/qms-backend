package org.example.management.repository;

import org.example.management.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {
    boolean existsByCode(String code);
    List<ServiceType> findByRequestGroupId(Long requestGroupId);
}

