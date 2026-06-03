package org.example.management.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.common.dto.ApiResponse;
import org.example.management.client.AuthServiceClient;
import org.example.management.dto.ServiceCounterDto;
import org.example.management.dto.ServiceCounterWithTicketDto;
import org.example.management.entity.Branch;
import org.example.management.entity.CustomerSegment;
import org.example.management.entity.RequestGroup;
import org.example.management.entity.ServiceCounter;
import org.example.management.entity.enums.CounterStatus;
import org.example.management.repository.BranchRepository;
import org.example.management.repository.CustomerSegmentRepository;
import org.example.management.repository.RequestGroupRepository;
import org.example.management.repository.ServiceCounterRepository;
import org.example.management.client.TicketServiceClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceCounterService {

    private final ServiceCounterRepository repository;
    private final BranchRepository branchRepository;
    private final RequestGroupRepository requestGroupRepository;
    private final CustomerSegmentRepository customerSegmentRepository;

    @Autowired(required = false)
    private TicketServiceClient ticketServiceClient;

    @Autowired(required = false)
    private AuthServiceClient authServiceClient;

    private ServiceCounterDto mapToDto(ServiceCounter entity) {
        return ServiceCounterDto.builder()
                .id(entity.getId())
                .branchId(entity.getBranch().getId())
                .code(entity.getCode())
                .name(entity.getName())
                .status(entity.getStatus())
                .isActive(entity.getIsActive())
                .requestGroupIds(entity.getRequestGroups() != null ? entity.getRequestGroups().stream().map(RequestGroup::getId).collect(Collectors.toSet()) : new HashSet<>())
                .customerSegmentIds(entity.getCustomerSegments() != null ? entity.getCustomerSegments().stream().map(CustomerSegment::getId).collect(Collectors.toSet()) : new HashSet<>())
                .build();
    }

    public List<ServiceCounterDto> getAll() {
        return repository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<ServiceCounterDto> getByBranchId(Long branchId) {
        return repository.findByBranchId(branchId).stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public ServiceCounterDto getById(Long id) {
        ServiceCounter entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Service counter not found"));
        return mapToDto(entity);
    }

    @Transactional
    public ServiceCounterDto create(ServiceCounterDto dto) {
        if (repository.existsByBranchIdAndCode(dto.getBranchId(), dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Counter code already exists in this branch");
        }

        Branch branch = branchRepository.findById(dto.getBranchId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Branch not found"));

        Set<RequestGroup> requestGroups = dto.getRequestGroupIds() != null ?
                new HashSet<>(requestGroupRepository.findAllById(dto.getRequestGroupIds())) : new HashSet<>();

        Set<CustomerSegment> customerSegments = dto.getCustomerSegmentIds() != null ?
                new HashSet<>(customerSegmentRepository.findAllById(dto.getCustomerSegmentIds())) : new HashSet<>();

        ServiceCounter entity = ServiceCounter.builder()
                .branch(branch)
                .code(dto.getCode())
                .name(dto.getName())
                .status(dto.getStatus() != null ? dto.getStatus() : CounterStatus.AVAILABLE)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .requestGroups(requestGroups)
                .customerSegments(customerSegments)
                .build();

        return mapToDto(repository.save(entity));
    }

    @Transactional
    public ServiceCounterDto update(Long id, ServiceCounterDto dto) {
        ServiceCounter entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Service counter not found"));

        if (!entity.getCode().equals(dto.getCode()) && repository.existsByBranchIdAndCode(dto.getBranchId(), dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Counter code already exists in this branch");
        }

        if (!entity.getBranch().getId().equals(dto.getBranchId())) {
            Branch branch = branchRepository.findById(dto.getBranchId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Branch not found"));
            entity.setBranch(branch);
        }

        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());

        if (dto.getRequestGroupIds() != null) {
            entity.setRequestGroups(new HashSet<>(requestGroupRepository.findAllById(dto.getRequestGroupIds())));
        }

        if (dto.getCustomerSegmentIds() != null) {
            entity.setCustomerSegments(new HashSet<>(customerSegmentRepository.findAllById(dto.getCustomerSegmentIds())));
        }

        return mapToDto(repository.save(entity));
    }

    /**
     * Lấy danh sách quầy theo branchId kèm theo vé đang được phục vụ (nếu có)
     * Đồng thời lấy trạng thái thực tế của quầy từ phiên làm việc ACTIVE trong ngày hiện tại
     * Tối ưu bằng cách gửi danh sách quầy một lần để lấy danh sách vé thay vì gọi lần lượt cho từng quầy
     */
    public List<ServiceCounterWithTicketDto> getCountersByBranchWithTickets(Long branchId) {
        // B1: Lấy danh sách quầy theo branchId
        List<ServiceCounter> counters = repository.findByBranchId(branchId);

        if (counters.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // B2: Lấy danh sách counterId
        List<Long> counterIds = counters.stream()
                .map(ServiceCounter::getId)
                .collect(Collectors.toList());

        // B2.5: Lấy danh sách counter IDs đang có phiên làm việc ACTIVE (để xác định trạng thái thực tế)
        Set<Long> activeCounterIds = new HashSet<>();
        if (authServiceClient != null) {
            try {
                ApiResponse<List<Long>> response = authServiceClient.getActiveCounterIds();
                if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                    activeCounterIds = new HashSet<>(response.getData());
                }
            } catch (Exception ignored) {
                // Nếu không lấy được thông tin phiên, tiếp tục với trạng thái từ DB
            }
        }

        // B3: Lấy danh sách vé hiện tại cho tất cả quầy (tối ưu hóa - 1 lần gọi thay vì n lần)
        Map<Long, Map<String, Object>> counterTicketsMap = new java.util.HashMap<>();
        if (ticketServiceClient != null) {
            try {
                ApiResponse<Map<Long, Map<String, Object>>> response = ticketServiceClient.getTicketsForCounters(counterIds);
                if (response != null && response.getData() != null) {
                    counterTicketsMap = response.getData();
                }
            } catch (Exception ignored) {
                // Nếu không lấy được vé, tiếp tục với danh sách quầy mà không có vé
            }
        }

        // B4: Build response với trạng thái thực tế
        Map<Long, Map<String, Object>> finalCounterTicketsMap = counterTicketsMap;
        Set<Long> finalActiveCounterIds = activeCounterIds;
        return counters.stream()
                .map(counter -> {
                    ServiceCounterWithTicketDto dto = new ServiceCounterWithTicketDto();
                    dto.setId(counter.getId());
                    dto.setBranchId(counter.getBranch().getId());
                    dto.setCode(counter.getCode());
                    dto.setName(counter.getName());
                    dto.setIsActive(counter.getIsActive());
                    dto.setRequestGroupIds(counter.getRequestGroups() != null ?
                            counter.getRequestGroups().stream().map(RequestGroup::getId).collect(Collectors.toSet()) :
                            new HashSet<>());
                    dto.setCustomerSegmentIds(counter.getCustomerSegments() != null ?
                            counter.getCustomerSegments().stream().map(CustomerSegment::getId).collect(Collectors.toSet()) :
                            new HashSet<>());

                    // *** Xác định trạng thái thực tế của quầy ***
                    // Nếu isActive = false → status = INACTIVE (quầy đã disable)
                    // Nếu isActive = true + có phiên ACTIVE → status = OCCUPIED (đang phục vụ)
                    // Nếu isActive = true + không có phiên → status = AVAILABLE (chưa hoạt động)
                    if (!counter.getIsActive()) {
                        // Quầy đã disable
                        dto.setStatus(CounterStatus.INACTIVE);
                    } else if (finalActiveCounterIds.contains(counter.getId())) {
                        // Quầy đang có phiên làm việc ACTIVE
                        dto.setStatus(CounterStatus.AVAILABLE);
                    } else {
                        // Quầy enable nhưng chưa có ai phục vụ
                        dto.setStatus(CounterStatus.OFFLINE);
                    }

                    // Gán vé hiện tại (nếu có)
                    if (finalCounterTicketsMap.containsKey(counter.getId())) {
                        Map<String, Object> ticketData = finalCounterTicketsMap.get(counter.getId());
                        if (ticketData != null) {
                            dto.setCurrentTicketId((Integer) ticketData.get("id"));
                            dto.setCurrentTicketNo((String) ticketData.get("ticketNo"));
                            dto.setCurrentTicketStatus(ticketData.get("status") != null ? ticketData.get("status").toString() : null);
                        }
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách subscription topics cho một quầy cụ thể
     * Topics được xây dựng dựa trên RequestGroupIds và CustomerSegmentIds của quầy
     * Format: /topic/branch/{branchId}/{requestGroupId}/{customerSegmentId}
     * 
     * @param counterId ID của quầy
     * @return List các subscription topics
     */
    public List<String> getSubscriptionTopicsByCounterId(Long counterId) {
        // B1: Lấy thông tin quầy
        ServiceCounter counter = repository.findById(counterId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Service counter not found"));

        // B2: Kiểm tra dữ liệu hợp lệ
        if (counter.getRequestGroups() == null || counter.getRequestGroups().isEmpty() ||
            counter.getCustomerSegments() == null || counter.getCustomerSegments().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // B3: Xây dựng danh sách topics
        Long branchId = counter.getBranch().getId();
        List<String> topics = new java.util.ArrayList<>();

        for (RequestGroup rg : counter.getRequestGroups()) {
            for (CustomerSegment segment : counter.getCustomerSegments()) {
                topics.add("/topic/branch/" + branchId + "/" + rg.getId() + "/" + segment.getId());
            }
        }

        return topics;
    }

    /**
     * Lấy danh sách subscription topics cho tất cả các quầy có status AVAILABLE
     * Topics được xây dựng dựa trên RequestGroupIds và CustomerSegmentIds của quầy
     * Format: /topic/branch/{branchId}/{requestGroupId}/{customerSegmentId}
     *
     * @return Map với key là counterId, value là List các subscription topics
     */
    public Map<Long, List<String>> getSubscriptionTopicsForAvailableCounters() {
        // B1: Lấy tất cả quầy có status = AVAILABLE
        List<ServiceCounter> availableCounters = repository.findAll().stream()
                .filter(counter -> counter.getStatus() == CounterStatus.AVAILABLE && counter.getIsActive())
                .collect(Collectors.toList());

        if (availableCounters.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        // B2: Xây dựng map topics cho mỗi quầy
        Map<Long, List<String>> result = new java.util.HashMap<>();

        for (ServiceCounter counter : availableCounters) {
            // Kiểm tra dữ liệu hợp lệ
            if (counter.getRequestGroups() == null || counter.getRequestGroups().isEmpty() ||
                counter.getCustomerSegments() == null || counter.getCustomerSegments().isEmpty()) {
                result.put(counter.getId(), java.util.Collections.emptyList());
                continue;
            }

            // Xây dựng danh sách topics
            Long branchId = counter.getBranch().getId();
            List<String> topics = new java.util.ArrayList<>();

            for (RequestGroup rg : counter.getRequestGroups()) {
                for (CustomerSegment segment : counter.getCustomerSegments()) {
                    topics.add("/topic/branch/" + branchId + "/" + rg.getId() + "/" + segment.getId());
                }
            }

            result.put(counter.getId(), topics);
        }

        return result;
    }

}
