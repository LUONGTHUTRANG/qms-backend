package org.example.management.repository;

import org.example.management.entity.RequestGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestGroupRepository extends JpaRepository<RequestGroup, Long> {
    boolean existsByCode(String code);
    boolean existsByPrefixCode(String prefixCode);
}

