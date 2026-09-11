package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListProgramEnrollmentAttendanceService implements ListProgramEnrollmentAttendanceUseCase {

    private final AttendanceRepository attendanceRepository;

    public ListProgramEnrollmentAttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    public List<Attendance> list(Long enrollmentId) {
        return attendanceRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
    }
}
