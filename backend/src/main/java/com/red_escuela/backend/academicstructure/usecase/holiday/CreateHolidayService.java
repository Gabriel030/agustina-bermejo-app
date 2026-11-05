package com.red_escuela.backend.academicstructure.usecase.holiday;

import com.red_escuela.backend.academicstructure.dto.CreateHolidayRequest;
import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.service.HolidayCreator;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateHolidayService implements CreateHolidayUseCase {

    private final HolidayCreator holidayCreator;

    public CreateHolidayService(HolidayCreator holidayCreator) {
        this.holidayCreator = holidayCreator;
    }

    @Override
    public HolidayResponse create(CreateHolidayRequest request) {
        return holidayCreator.create(request);
    }
}
