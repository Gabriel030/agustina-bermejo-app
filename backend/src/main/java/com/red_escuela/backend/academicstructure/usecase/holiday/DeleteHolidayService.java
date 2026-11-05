package com.red_escuela.backend.academicstructure.usecase.holiday;

import com.red_escuela.backend.academicstructure.service.HolidayDeleter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeleteHolidayService implements DeleteHolidayUseCase {

    private final HolidayDeleter holidayDeleter;

    public DeleteHolidayService(HolidayDeleter holidayDeleter) {
        this.holidayDeleter = holidayDeleter;
    }

    @Override
    public void delete(Long id) {
        holidayDeleter.delete(id);
    }
}
