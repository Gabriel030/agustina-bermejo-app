package com.red_escuela.backend.enrollment.usecase.occupancy;

import com.red_escuela.backend.enrollment.dto.CycleOccupancyResponse;

public interface GetCycleOccupancyUseCase {
    CycleOccupancyResponse getOccupancy(Long cycleId);
}
