package org.example.management.service;

import lombok.RequiredArgsConstructor;
import org.example.management.dto.CustomerSegmentCheckRequest;
import org.example.management.dto.CustomerSegmentCheckResponse;
import org.example.management.entity.PriorityCustomerMock;
import org.example.management.repository.PriorityCustomerMockRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriorityCustomerMockService {

    private final PriorityCustomerMockRepository repository;

    public CustomerSegmentCheckResponse checkCustomerSegment(CustomerSegmentCheckRequest request) {
        Optional<PriorityCustomerMock> customerOpt = repository.findByPhoneNumberAndCustomerSegmentIdAndIsActiveTrue(
                request.getPhoneNumber(), request.getCustomerSegmentId()
        );

        if (customerOpt.isPresent()) {
            return CustomerSegmentCheckResponse.builder()
                    .isValid(true)
                    .fullName(customerOpt.get().getFullName())
                    .build();
        }

        return CustomerSegmentCheckResponse.builder()
                .isValid(false)
                .fullName(null)
                .build();
    }
}

