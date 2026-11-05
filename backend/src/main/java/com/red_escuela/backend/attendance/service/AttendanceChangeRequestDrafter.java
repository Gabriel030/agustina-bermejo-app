package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import org.springframework.stereotype.Service;

@Service
public class AttendanceChangeRequestDrafter {

    private final AttendanceSectionAccess sectionAccess;

    public AttendanceChangeRequestDrafter(AttendanceSectionAccess sectionAccess) {
        this.sectionAccess = sectionAccess;
    }

    public AttendanceChangeRequest draftPendingRequest(Attendance attendance, Long instructorId,
                                                       CreateAttendanceChangeRequestRequest body) {
        AttendanceChangeRequest request = new AttendanceChangeRequest();
        request.setAttendance(attendance);
        request.setRequestedBy(sectionAccess.resolveInstructor(instructorId));
        request.setPreviousStatus(attendance.getStatus());
        request.setRequestedStatus(body.requestedStatus());
        request.setReason(body.reason());
        request.setStatus(AttendanceChangeRequestStatus.PENDING);
        return request;
    }
}
