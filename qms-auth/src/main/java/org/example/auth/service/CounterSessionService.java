package org.example.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.auth.context.UserContext;
import org.example.auth.context.UserContextHolder;
import org.example.auth.dto.CounterSessionCreateRequest;
import org.example.auth.dto.CounterSessionDto;
import org.example.auth.entity.AppUser;
import org.example.auth.entity.CounterSession;
import org.example.auth.entity.enums.CounterSessionStatus;
import org.example.auth.event.CounterSessionStartedEvent;
import org.example.auth.repository.AppUserRepository;
import org.example.auth.repository.CounterSessionRepository;
import org.example.common.exception.BusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CounterSessionService {

    private final CounterSessionRepository sessionRepository;
    private final AppUserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

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
             throw new BusinessException(HttpStatus.CONFLICT, "Quầy đã được phục vụ bởi nhân viên khác. Vui lòng chọn đúng quầy.");
        }

        CounterSession newSession = CounterSession.builder()
                .user(user)
                .counterId(request.getCounterId())
                .branchId(requestBranchId != null ? requestBranchId : user.getBranchId())
                .status(CounterSessionStatus.ACTIVE)
                .startedAt(LocalDateTime.now())
                .build();

        CounterSession savedSession = sessionRepository.save(newSession);

        // Phát đi sự kiện session started cho quầy đó
        eventPublisher.publishEvent(new CounterSessionStartedEvent(this, savedSession, user.getFullName()));

        return mapToDto(savedSession);
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

     /**
      * End all active sessions (used for day-end or app cleanup)
      * This method is called automatically and doesn't require UserContext
      * @return List of ended sessions
      */
     @Transactional
     public List<CounterSessionDto> endAllActiveSessions() {
         List<CounterSession> activeSessions = sessionRepository.findAllByStatus(CounterSessionStatus.ACTIVE);
         
         if (activeSessions.isEmpty()) {
             return List.of();
         }

         LocalDateTime now = LocalDateTime.now();
         for (CounterSession session : activeSessions) {
             session.setStatus(CounterSessionStatus.CLOSED);
             session.setEndedAt(now);
         }

         List<CounterSession> endedSessions = sessionRepository.saveAll(activeSessions);
         return endedSessions.stream()
                 .map(this::mapToDto)
                 .collect(Collectors.toList());
     }

     /**
      * Get count of active sessions (for monitoring)
      */
     @Transactional(readOnly = true)
     public long getActiveSessionCount() {
         return sessionRepository.findAllByStatus(CounterSessionStatus.ACTIVE).size();
     }

      /**
       * Lấy danh sách counter IDs đang có phiên làm việc ACTIVE
       * Dùng cho qms-management để xác định trạng thái thực tế của quầy
       * @return List các counter ID có phiên ACTIVE
       */
      @Transactional(readOnly = true)
      public List<Long> getActiveCounterIds() {
          return sessionRepository.findAllByStatus(CounterSessionStatus.ACTIVE)
                  .stream()
                  .map(CounterSession::getCounterId)
                  .collect(Collectors.toList());
      }

      /**
       * Lấy thông tin về người đang phục vụ counter có ID được chỉ định
       * Kiểm tra xem có session ACTIVE cho counter đó hay không
       * @param counterId ID của quầy
       * @return Thông tin session và người phục vụ nếu counter đang được phục vụ
       * @throws BusinessException nếu không có session ACTIVE cho counter đó
       */
      @Transactional(readOnly = true)
      public CounterSessionDto getActiveSessionByCounterId(Long counterId) {
          CounterSession session = sessionRepository.findByCounterIdAndStatus(counterId, CounterSessionStatus.ACTIVE)
                  .stream()
                  .findFirst()
                  .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Quầy không có phiên làm việc ACTIVE"));
          return mapToDto(session);
      }
}
