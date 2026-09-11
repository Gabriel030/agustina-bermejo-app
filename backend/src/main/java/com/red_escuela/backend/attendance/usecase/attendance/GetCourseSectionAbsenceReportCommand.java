package com.red_escuela.backend.attendance.usecase.attendance;

public record GetCourseSectionAbsenceReportCommand(Long sectionId, Long instructorIdIfDocente, Integer minAbsences) {
}
