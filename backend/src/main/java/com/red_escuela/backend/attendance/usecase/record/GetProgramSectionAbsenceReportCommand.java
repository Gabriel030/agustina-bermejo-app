package com.red_escuela.backend.attendance.usecase.record;

public record GetProgramSectionAbsenceReportCommand(Long sectionId, Long instructorIdIfDocente, Integer minAbsences) {
}
