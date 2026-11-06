package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceFinder;
import com.red_escuela.backend.attendance.exception.InvalidJustificationInputException;
import com.red_escuela.backend.attendance.service.AttendanceJustificationRecorder;
import com.red_escuela.backend.attendance.service.JustificationDeadlinePolicy;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class JustifyAttendanceService implements JustifyAttendanceUseCase {

    private static final int REASON_MAX_LENGTH = 500;

    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceFinder attendanceFinder;
    private final AttendanceJustificationRecorder justificationRecorder;
    private final JustificationDeadlinePolicy deadlinePolicy;

    public JustifyAttendanceService(AttendanceRepository attendanceRepository,
                                     AttendanceFinder attendanceFinder,
                                     AttendanceJustificationRecorder justificationRecorder,
                                     JustificationDeadlinePolicy deadlinePolicy,
                                     ApplicationEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
        this.justificationRecorder = justificationRecorder;
        this.deadlinePolicy = deadlinePolicy;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Attendance justify(JustifyAttendanceCommand command) {
        Attendance attendance = attendanceFinder.findOrThrow(command.attendanceId());
        if (attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new CannotJustifyException(
                    "La asistencia " + command.attendanceId() + " no está ABSENT, no se puede justificar");
        }
        String reason = reasonWithException(attendance, command);
        justificationRecorder.markJustifiedByCurrentActor(attendance);
        attendance.setJustificationReason(reason);
        attendance.setJustificationAttachmentUrl(command.attachmentUrl());
        Attendance saved = attendanceRepository.save(attendance);
        eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved));
        return saved;
    }

    private String reasonWithException(Attendance attendance, JustifyAttendanceCommand command) {
        if (deadlinePolicy.isWithinDeadline(attendance.getClassDate())) {
            return command.reason();
        }
        if (command.exceptionReason() == null || command.exceptionReason().isBlank()) {
            throw new InvalidJustificationInputException(
                    "Fuera de plazo hay que indicar el motivo de la excepción");
        }
        String combined = command.reason() + " [Excepción fuera de plazo: " + command.exceptionReason() + "]";
        return combined.length() > REASON_MAX_LENGTH ? combined.substring(0, REASON_MAX_LENGTH) : combined;
    }
}
