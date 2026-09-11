package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RejectAttendanceJustificationService implements RejectAttendanceJustificationUseCase {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceFinder attendanceFinder;

    public RejectAttendanceJustificationService(AttendanceRepository attendanceRepository, AttendanceFinder attendanceFinder) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceFinder = attendanceFinder;
    }

    @Override
    @Transactional
    public Attendance reject(Long attendanceId) {
        Attendance attendance = attendanceFinder.findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.JUSTIFICATION_PENDING) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no tiene una justificación pendiente");
        }
        attendance.setStatus(AttendanceStatus.ABSENT);
        return attendanceRepository.save(attendance);
    }
}
