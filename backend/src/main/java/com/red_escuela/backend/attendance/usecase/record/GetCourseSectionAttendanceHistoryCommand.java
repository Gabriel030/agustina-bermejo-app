package com.red_escuela.backend.attendance.usecase.record;

public record GetCourseSectionAttendanceHistoryCommand(Long sectionId, Long instructorIdIfDocente) {
}
