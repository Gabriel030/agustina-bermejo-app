package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.HolidayResponse;
import com.red_escuela.backend.repository.HolidayRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HolidayController {

    private final HolidayRepository holidayRepository;

    public HolidayController(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @GetMapping("/holidays")
    public List<HolidayResponse> list() {
        return holidayRepository.findAll().stream().map(HolidayResponse::from).toList();
    }
}
