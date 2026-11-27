package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.domain.AttendanceDecisionRevertWindow;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.exception.AttendanceChangeRequestNotDecidedException;
import com.red_escuela.backend.attendance.exception.AttendanceDecisionRevertWindowExpiredException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Component;

@Component
public class AttendanceDecisionRevertPolicy {

    public void requireRevertible(AttendanceChangeRequest request) {
        if (request.getStatus() == AttendanceChangeRequestStatus.PENDING || request.getApprovedAt() == null) {
            throw new AttendanceChangeRequestNotDecidedException(request.getId());
        }
        if (!AttendanceDecisionRevertWindow.isOpen(request.getApprovedAt(), ApplicationTime.today())) {
            throw new AttendanceDecisionRevertWindowExpiredException(request.getId());
        }
    }
}
