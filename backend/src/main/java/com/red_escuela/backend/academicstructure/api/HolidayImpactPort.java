package com.red_escuela.backend.academicstructure.api;

import java.time.LocalDate;
import java.util.List;

public interface HolidayImpactPort {
    List<HolidayAffectedItem> findAffectedOn(LocalDate date);
}
