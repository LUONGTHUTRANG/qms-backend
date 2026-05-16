package org.example.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.auth.context.UserContext;
import org.example.auth.context.UserContextHolder;
import org.example.auth.dto.CounterSessionCreateRequest;
import org.example.auth.dto.CounterSessionDto;
import org.example.auth.entity.AppUser;
import org.example.auth.entity.CounterSession;
import org.example.auth.entity.enums.CounterSessionStatus;
import org.example.auth.repository.AppUserRepository;
import org.example.auth.repository.CounterSessionRepository;
import org.example.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CounterSessionService {

    private final CounterSessionRepository sessionRepository;
    private final AppUserRepository userRepository;

    private CounterSessionDto mapToDto(CounterSession entity) {
        return CounterSessionDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .counterId(entity.getCounterId())
                .branchId(entity.getBranchId())
                .status(entity.getStatus())
                .fullName(entity.getUser().getFullName())
                .startedAt(entity.getStartedAt())
                .endedAt(entity.getEndedAt())
                .build();
    }

    @Transactional
    public CounterSessionDto startSession(CounterSessionCreateRequest request) {
        // Using ContextHolder as requested
        UserContext ctx = UserContextHolder.getContext();
        if (ctx == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "User authentication context is missing or invalid");
        }
        Long currentUserId = ctx.getUserId();
        Long requestBranchId = ctx.getBranchId();

        AppUser user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));

        // Business Rule: One user can only be active at one counter at a time.
        sessionRepository.findByUserIdAndStatus(currentUserId, CounterSessionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw new BusinessException(HttpStatus.CONFLICT, "User is already active at a counter");
                });

        // Business Rule: One counter can only be occupied by one user at a time. (Usually applied, checking it here)
        List<CounterSession> activeSessions = sessionRepository.findByCounterIdAndStatus(request.getCounterId(), CounterSessionStatus.ACTIVE);
        if (!activeSessions.isEmpty()) {
             throw new BusinessException(HttpStatus.CONFLICT, "Counter is already occupied by another user");
        }

        CounterSession newSession = CounterSession.builder()
                .user(user)
                .counterId(request.getCounterId())
                .branchId(requestBranchId != null ? requestBranchId : user.getBranchId())
                .status(CounterSessionStatus.ACTIVE)
                .startedAt(LocalDateTime.now())
                .build();

        return mapToDto(sessionRepository.save(newSession));
    }

    @Transactional(readOnly = true)
    public CounterSessionDto getActiveSessionByUserId(Long userId) {
         CounterSession session = sessionRepository.findByUserIdAndStatus(userId, CounterSessionStatus.ACTIVE)
                 .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User has no active counter session"));
         return mapToDto(session);
    }

    @Transactional
    public CounterSessionDto endSession() {
        UserContext ctx = UserContextHolder.getContext();
        if (ctx == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "User authentication context is missing or invalid");
        }
        Long currentUserId = ctx.getUserId();

        CounterSession session = sessionRepository.findByUserIdAndStatus(currentUserId, CounterSessionStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User has no active counter session"));

        session.setStatus(CounterSessionStatus.CLOSED);
        session.setEndedAt(LocalDateTime.now());

        return mapToDto(sessionRepository.save(session));
    }
}
