package com.red_escuela.backend.academicstructure.usecase.holiday;

import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateHolidayRequest;

public interface UpdateHolidayUseCase {
    HolidayResponse update(Long id, UpdateHolidayRequest request);
}
