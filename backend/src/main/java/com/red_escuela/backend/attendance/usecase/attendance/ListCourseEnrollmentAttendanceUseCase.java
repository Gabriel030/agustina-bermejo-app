package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;

import java.util.List;

public interface ListCourseEnrollmentAttendanceUseCase {
    List<Attendance> list(Long enrollmentId);
}
