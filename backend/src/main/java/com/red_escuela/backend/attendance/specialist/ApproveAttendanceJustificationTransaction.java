package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.events.AttendanceJustificationApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApproveAttendanceJustificationTransaction {

    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceFinder attendanceFinder;
    private final AttendanceJustificationRecorder justificationRecorder;

    public ApproveAttendanceJustificationTransaction(AttendanceRepository attendanceRepository,
                                                     AttendanceFinder attendanceFinder,
                                                     AttendanceJustificationRecorder justificationRecorder,
                                                     ApplicationEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
        this.justificationRecorder = justificationRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Attendance approve(Long attendanceId) {
        Attendance attendance = attendanceFinder.findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.JUSTIFICATION_PENDING) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no tiene una justificación pendiente");
        }
        justificationRecorder.markJustifiedByCurrentActor(attendance);
        Attendance saved = attendanceRepository.save(attendance);
        eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved));
        eventPublisher.publishEvent(AttendanceJustificationApprovedEvent.approvedFor(saved));
        return saved;
    }
}
