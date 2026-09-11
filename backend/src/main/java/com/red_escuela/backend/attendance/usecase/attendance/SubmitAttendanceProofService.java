package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class SubmitAttendanceProofService implements SubmitAttendanceProofUseCase {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceFinder attendanceFinder;

    public SubmitAttendanceProofService(AttendanceRepository attendanceRepository, AttendanceFinder attendanceFinder) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
    }

    @Override
    @Transactional
    public Attendance submit(SubmitAttendanceProofCommand command) {
        Attendance attendance = attendanceFinder.findOrThrow(command.attendanceId());
        if (attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new CannotJustifyException(
                    "La asistencia " + command.attendanceId() + " no está ABSENT, no se puede justificar");
        }
        attendance.setStatus(AttendanceStatus.JUSTIFICATION_PENDING);
        attendance.setJustificationReason(command.reason());
        attendance.setJustificationAttachmentUrl(command.attachmentUrl());
        return attendanceRepository.save(attendance);
    }
}
