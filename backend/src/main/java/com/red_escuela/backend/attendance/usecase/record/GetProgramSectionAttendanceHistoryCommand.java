package com.red_escuela.backend.attendance.usecase.record;

public record GetProgramSectionAttendanceHistoryCommand(Long sectionId, Long instructorIdIfDocente) {
}
