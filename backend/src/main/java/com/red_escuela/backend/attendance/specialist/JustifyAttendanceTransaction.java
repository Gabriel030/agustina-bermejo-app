package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JustifyAttendanceTransaction {

    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceFinder attendanceFinder;
    private final AttendanceJustificationRecorder justificationRecorder;

    public JustifyAttendanceTransaction(AttendanceRepository attendanceRepository,
                                        AttendanceFinder attendanceFinder,
                                        AttendanceJustificationRecorder justificationRecorder,
                                        ApplicationEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
        this.justificationRecorder = justificationRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Attendance justify(Long attendanceId, String reason, String attachmentUrl, String exceptionReason) {
        Attendance attendance = attendanceFinder.findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no está ABSENT, no se puede justificar");
        }
        justificationRecorder.justifyByStaff(attendance, reason, attachmentUrl, exceptionReason);
        Attendance saved = attendanceRepository.save(attendance);
        eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved));
        return saved;
    }
}
