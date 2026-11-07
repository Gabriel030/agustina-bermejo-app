package com.red_escuela.backend.attendance.usecase.record;

public record GetCourseSectionAbsenceReportCommand(Long sectionId, Long instructorIdIfDocente, Integer minAbsences) {
}
