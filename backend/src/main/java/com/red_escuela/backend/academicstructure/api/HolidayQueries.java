package com.red_escuela.backend.academicstructure.api;

import java.time.LocalDate;

public interface HolidayQueries {

    boolean isHoliday(LocalDate date);
}
