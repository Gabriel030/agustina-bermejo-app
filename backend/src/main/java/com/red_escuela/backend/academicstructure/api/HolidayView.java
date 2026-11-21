package com.red_escuela.backend.academicstructure.api;

import java.time.LocalDate;

public record HolidayView(LocalDate date, String name, String type) {
}
