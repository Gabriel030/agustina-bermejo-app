package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class JustifyAttendanceService implements JustifyAttendanceUseCase {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceFinder attendanceFinder;
    private final CurrentPersonService currentPersonService;

    public JustifyAttendanceService(AttendanceRepository attendanceRepository,
                                     AttendanceFinder attendanceFinder,
                                     CurrentPersonService currentPersonService) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public Attendance justify(JustifyAttendanceCommand command) {
        Attendance attendance = attendanceFinder.findOrThrow(command.attendanceId());
        if (attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new CannotJustifyException(
                    "La asistencia " + command.attendanceId() + " no está ABSENT, no se puede justificar");
        }
        Person justifiedBy = currentPersonService.resolveCurrentPerson();

        attendance.setStatus(AttendanceStatus.JUSTIFIED);
        attendance.setJustificationReason(command.reason());
        attendance.setJustificationAttachmentUrl(command.attachmentUrl());
        attendance.setJustifiedBy(justifiedBy);
        attendance.setJustifiedAt(LocalDateTime.now());
        return attendanceRepository.save(attendance);
    }
}
