package com.red_escuela.backend.academicstructure.usecase.holiday;

import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListHolidaysService implements ListHolidaysUseCase {

    private final HolidayRepository holidayRepository;

    public ListHolidaysService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Override
    public List<HolidayResponse> list() {
        return holidayRepository.findAll().stream().map(HolidayResponse::from).toList();
    }
}
