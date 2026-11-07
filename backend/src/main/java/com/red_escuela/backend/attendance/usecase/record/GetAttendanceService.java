package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.specialist.AttendanceFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetAttendanceService implements GetAttendanceUseCase {

    private final AttendanceFinder attendanceFinder;

    public GetAttendanceService(AttendanceFinder attendanceFinder) {
        this.attendanceFinder = attendanceFinder;
    }

    @Override
    public Attendance get(Long id) {
        return attendanceFinder.findOrThrow(id);
    }
}
