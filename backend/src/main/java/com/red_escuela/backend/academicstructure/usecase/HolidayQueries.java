package com.red_escuela.backend.academicstructure.usecase;

import java.time.LocalDate;

public interface HolidayQueries {

    boolean isHoliday(LocalDate date);
}
