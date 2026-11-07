package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;

import java.util.List;

public interface LoadProgramSectionAttendanceUseCase {
    List<Attendance> load(LoadProgramSectionAttendanceCommand command);
}
