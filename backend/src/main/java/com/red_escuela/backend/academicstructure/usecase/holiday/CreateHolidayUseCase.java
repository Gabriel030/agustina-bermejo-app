package com.red_escuela.backend.academicstructure.usecase.holiday;

import com.red_escuela.backend.academicstructure.dto.CreateHolidayRequest;
import com.red_escuela.backend.academicstructure.dto.HolidayResponse;

public interface CreateHolidayUseCase {
    HolidayResponse create(CreateHolidayRequest request);
}
