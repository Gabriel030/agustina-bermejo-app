package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.attendance.service.AttendanceStatusChangedEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class ApproveAttendanceJustificationService implements ApproveAttendanceJustificationUseCase {

    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceFinder attendanceFinder;
    private final CurrentPersonService currentPersonService;

    public ApproveAttendanceJustificationService(AttendanceRepository attendanceRepository,
                                                  AttendanceFinder attendanceFinder,
                                                  CurrentPersonService currentPersonService,
                                     ApplicationEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Attendance approve(Long attendanceId) {
        Attendance attendance = attendanceFinder.findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.JUSTIFICATION_PENDING) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no tiene una justificación pendiente");
        }
        Person justifiedBy = currentPersonService.resolveCurrentPerson();

        attendance.setStatus(AttendanceStatus.JUSTIFIED);
        attendance.setJustifiedBy(justifiedBy);
        attendance.setJustifiedAt(ApplicationTime.now());
        Attendance saved = attendanceRepository.save(attendance);
        eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved));
        return saved;
    }
}
