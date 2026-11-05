package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceFinder;
import com.red_escuela.backend.attendance.service.AttendanceJustificationRecorder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class JustifyAttendanceService implements JustifyAttendanceUseCase {

    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceFinder attendanceFinder;
    private final AttendanceJustificationRecorder justificationRecorder;

    public JustifyAttendanceService(AttendanceRepository attendanceRepository,
                                     AttendanceFinder attendanceFinder,
                                     AttendanceJustificationRecorder justificationRecorder,
                                     ApplicationEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
        this.justificationRecorder = justificationRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Attendance justify(JustifyAttendanceCommand command) {
        Attendance attendance = attendanceFinder.findOrThrowOwnedBy(command.attendanceId(), command.instructorIdIfDocente());
        if (attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new CannotJustifyException(
                    "La asistencia " + command.attendanceId() + " no está ABSENT, no se puede justificar");
        }
        justificationRecorder.markJustifiedByCurrentActor(attendance);
        attendance.setJustificationReason(command.reason());
        attendance.setJustificationAttachmentUrl(command.attachmentUrl());
        Attendance saved = attendanceRepository.save(attendance);
        eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved));
        return saved;
    }
}
