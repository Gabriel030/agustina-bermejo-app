package com.red_escuela.backend.academicstructure.usecase.holiday;

import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateHolidayRequest;
import com.red_escuela.backend.academicstructure.service.HolidayUpdater;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateHolidayService implements UpdateHolidayUseCase {

    private final HolidayUpdater holidayUpdater;

    public UpdateHolidayService(HolidayUpdater holidayUpdater) {
        this.holidayUpdater = holidayUpdater;
    }

    @Override
    public HolidayResponse update(Long id, UpdateHolidayRequest request) {
        return holidayUpdater.update(id, request);
    }
}
