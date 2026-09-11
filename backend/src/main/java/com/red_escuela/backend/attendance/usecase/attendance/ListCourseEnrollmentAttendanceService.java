package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListCourseEnrollmentAttendanceService implements ListCourseEnrollmentAttendanceUseCase {

    private final AttendanceRepository attendanceRepository;

    public ListCourseEnrollmentAttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    public List<Attendance> list(Long enrollmentId) {
        return attendanceRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
    }
}
