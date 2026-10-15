package com.red_escuela.backend.academicstructure.api;

import java.time.LocalDate;

public interface BusinessDayCalendar {
    boolean isBusinessDay(LocalDate date);
}
