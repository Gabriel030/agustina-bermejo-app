package com.red_escuela.backend.academicstructure.api;

import java.time.LocalDate;

public record HolidayAffectedItem(String kind, Long referenceId, LocalDate date) {
}
