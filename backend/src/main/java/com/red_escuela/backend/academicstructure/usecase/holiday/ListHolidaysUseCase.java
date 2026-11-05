package com.red_escuela.backend.academicstructure.usecase.holiday;

import com.red_escuela.backend.academicstructure.dto.HolidayResponse;

import java.util.List;

public interface ListHolidaysUseCase {
    List<HolidayResponse> list();
}
