package com.red_escuela.backend.attendance.usecase.record;

import java.time.LocalDate;

public record CancelCourseSectionClassSessionCommand(Long sectionId, Long instructorId, LocalDate classDate) {
}
