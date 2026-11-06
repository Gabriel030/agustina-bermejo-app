package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.exception.JustificationDeadlineExpiredException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AttendanceProofSubmitter {

    private final AttendanceRepository attendanceRepository;

    private final JustificationDeadlinePolicy deadlinePolicy;

    public AttendanceProofSubmitter(AttendanceRepository attendanceRepository, JustificationDeadlinePolicy deadlinePolicy) {
        this.attendanceRepository = attendanceRepository;
        this.deadlinePolicy = deadlinePolicy;
    }

    @Transactional
    public StudentAttendanceView submitProof(Long attendanceId, String reason, String attachmentUrl) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));
        if (attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no está ABSENT, no se puede justificar");
        }
        if (!deadlinePolicy.isWithinDeadline(attendance.getClassDate())) {
            throw new JustificationDeadlineExpiredException(attendanceId);
        }
        attendance.setStatus(AttendanceStatus.JUSTIFICATION_PENDING);
        attendance.setJustificationReason(reason);
        attendance.setJustificationAttachmentUrl(attachmentUrl);
        attendance.setJustificationRejectionReason(null);
        attendance.setJustificationRejectedAt(null);
        return StudentAttendanceView.from(attendanceRepository.save(attendance));
    }
}
