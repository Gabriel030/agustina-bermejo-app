package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CycleOccupancyResponse;
import com.red_escuela.backend.enrollment.usecase.occupancy.GetCycleOccupancyUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
    @RequiresPermission(code = "enrollment.read_waitlists", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CycleOccupancyResponse getOccupancy(@PathVariable Long id) {
        return getCycleOccupancyUseCase.getOccupancy(id);
    }
}
