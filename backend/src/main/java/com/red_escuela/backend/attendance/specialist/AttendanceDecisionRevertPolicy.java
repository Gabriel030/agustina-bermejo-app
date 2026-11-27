package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.exception.AttendanceChangeRequestNotDecidedException;
import com.red_escuela.backend.attendance.exception.AttendanceDecisionRevertWindowExpiredException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AttendanceDecisionRevertPolicy {

    static final int REVERT_WINDOW_DAYS = 7;

    public void requireRevertible(AttendanceChangeRequest request) {
        if (request.getStatus() == AttendanceChangeRequestStatus.PENDING || request.getApprovedAt() == null) {
            throw new AttendanceChangeRequestNotDecidedException(request.getId());
        }
        LocalDateTime deadline = request.getApprovedAt().plusDays(REVERT_WINDOW_DAYS);
        if (ApplicationTime.now().isAfter(deadline)) {
            throw new AttendanceDecisionRevertWindowExpiredException(request.getId());
        }
    }
}
