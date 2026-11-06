package com.red_escuela.backend.enrollment.usecase.occupancy;

import com.red_escuela.backend.enrollment.dto.CycleOccupancyResponse;
import com.red_escuela.backend.enrollment.service.CycleOccupancyReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCycleOccupancyService implements GetCycleOccupancyUseCase {

    private final CycleOccupancyReader cycleOccupancyReader;

    public GetCycleOccupancyService(CycleOccupancyReader cycleOccupancyReader) {
        this.cycleOccupancyReader = cycleOccupancyReader;
    }

    @Override
    public CycleOccupancyResponse getOccupancy(Long cycleId) {
        return cycleOccupancyReader.readOccupancy(cycleId);
    }
}
