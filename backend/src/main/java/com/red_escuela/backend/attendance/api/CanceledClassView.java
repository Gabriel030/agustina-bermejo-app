package com.red_escuela.backend.attendance.api;

import java.time.LocalDate;

public record CanceledClassView(Long sectionId, LocalDate date) {
}
