package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.events.AttendanceJustificationRejectedEvent;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceJustificationRejector {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceFinder attendanceFinder;
    private final ApplicationEventPublisher eventPublisher;

    public AttendanceJustificationRejector(AttendanceRepository attendanceRepository, AttendanceFinder attendanceFinder,
                                           ApplicationEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Attendance rejectPendingJustification(Long attendanceId, String reason) {
        Attendance attendance = attendanceFinder.findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.JUSTIFICATION_PENDING) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no tiene una justificación pendiente");
        }
        attendance.setStatus(AttendanceStatus.ABSENT);
        attendance.setJustificationRejectionReason(reason.trim());
        attendance.setJustificationRejectedAt(ApplicationTime.now());
        Attendance saved = attendanceRepository.save(attendance);
        eventPublisher.publishEvent(AttendanceJustificationRejectedEvent.rejectedFor(saved));
        return saved;
    }
}
