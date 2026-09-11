package com.red_escuela.backend.attendance.usecase.attendance;

public record GetProgramSectionAttendanceHistoryCommand(Long sectionId, Long instructorIdIfDocente) {
}
