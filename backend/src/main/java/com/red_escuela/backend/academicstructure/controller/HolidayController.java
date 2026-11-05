package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CreateHolidayRequest;
import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.usecase.holiday.CreateHolidayUseCase;
import com.red_escuela.backend.academicstructure.usecase.holiday.DeleteHolidayUseCase;
import com.red_escuela.backend.academicstructure.usecase.holiday.ListHolidaysUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HolidayController {

    private final ListHolidaysUseCase listHolidaysUseCase;
    private final CreateHolidayUseCase createHolidayUseCase;
    private final DeleteHolidayUseCase deleteHolidayUseCase;

    public HolidayController(ListHolidaysUseCase listHolidaysUseCase, CreateHolidayUseCase createHolidayUseCase,
                             DeleteHolidayUseCase deleteHolidayUseCase) {
        this.listHolidaysUseCase = listHolidaysUseCase;
        this.createHolidayUseCase = createHolidayUseCase;
        this.deleteHolidayUseCase = deleteHolidayUseCase;
    }

    @GetMapping("/holidays")
    public List<HolidayResponse> list() {
        return listHolidaysUseCase.list();
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public HolidayResponse create(@Valid @RequestBody CreateHolidayRequest request) {
        return createHolidayUseCase.create(request);
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void delete(@PathVariable Long id) {
        deleteHolidayUseCase.delete(id);
    }
}
