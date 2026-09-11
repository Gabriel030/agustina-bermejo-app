package com.red_escuela.backend.attendance.usecase.attendance;

import java.time.LocalDate;

public record GetProgramSectionClassSessionCommand(Long sectionId, Long instructorId, LocalDate classDate) {
}
