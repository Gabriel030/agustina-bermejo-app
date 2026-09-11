package com.red_escuela.backend.attendance.usecase.attendance;

public record GetCourseSectionAttendanceHistoryCommand(Long sectionId, Long instructorIdIfDocente) {
}
