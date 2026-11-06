package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CycleOccupancyResponse;
import com.red_escuela.backend.enrollment.usecase.occupancy.GetCycleOccupancyUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CycleOccupancyController {

    private final GetCycleOccupancyUseCase getCycleOccupancyUseCase;

    public CycleOccupancyController(GetCycleOccupancyUseCase getCycleOccupancyUseCase) {
        this.getCycleOccupancyUseCase = getCycleOccupancyUseCase;
    }

    @GetMapping("/academic-offering-cycles/{id}/occupancy")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public CycleOccupancyResponse getOccupancy(@PathVariable Long id) {
        return getCycleOccupancyUseCase.getOccupancy(id);
    }
}
