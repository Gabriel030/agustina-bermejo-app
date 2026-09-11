package com.red_escuela.backend.attendance.usecase.attendance;

public record GetProgramSectionAbsenceReportCommand(Long sectionId, Long instructorIdIfDocente, Integer minAbsences) {
}
