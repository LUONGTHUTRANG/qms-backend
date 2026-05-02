package org.example.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfoDto {
    private Long counterId;              // ID của quầy
    private Long userId;                 // ID của người dùng
    private Integer waitingCount;        // Số người đang chờ (WAITING)
    private Integer completedCount;      // Số người đã phục vụ xong (DONE)
    private Long sessionDurationSeconds; // Tổng thời gian phiên (gọi + phục vụ) từ lastCalledAt đến doneAt
}
