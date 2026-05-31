package org.example.auth.event;

import lombok.Getter;
import org.example.auth.entity.CounterSession;
import org.springframework.context.ApplicationEvent;

/**
 * Event phát đi khi một counter session được bắt đầu
 * Chứa thông tin về phiên làm việc tại quầy bao gồm:
 * - Counter ID: Mã của quầy
 * - Người phục vụ (fullName): Tên đầy đủ của nhân viên
 * - Branch ID: Chi nhánh của quầy
 */
@Getter
public class CounterSessionStartedEvent extends ApplicationEvent {
    private final CounterSession session;
    private final String fullName; // Tên người bắt đầu phục vụ session

    public CounterSessionStartedEvent(Object source, CounterSession session, String fullName) {
        super(source);
        this.session = session;
        this.fullName = fullName;
    }
}

