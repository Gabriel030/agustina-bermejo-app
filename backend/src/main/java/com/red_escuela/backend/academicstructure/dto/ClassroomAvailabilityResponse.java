package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Classroom;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record ClassroomAvailabilityResponse(
        Long id,
        String name,
        Integer capacity,
        boolean available,
        @Nullable Integer capacityShortfall,
        @Nullable Boolean overlapsInDates,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) ClassroomSlotOccupancyResponse occupant
) {
    public static ClassroomAvailabilityResponse of(Classroom c, boolean available) {
        return new ClassroomAvailabilityResponse(c.getId(), c.getName(), c.getCapacity(), available, null, null, null);
    }

    public static ClassroomAvailabilityResponse of(Classroom c, boolean available, Integer seats, ClassroomSlotOccupancyResponse occupant) {
        Integer shortfall = seats != null && c.getCapacity() != null && c.getCapacity() < seats ? seats - c.getCapacity() : null;
        Boolean overlaps = occupant == null ? null : occupant.overlapsInDates();
        return new ClassroomAvailabilityResponse(c.getId(), c.getName(), c.getCapacity(), available, shortfall, overlaps, occupant);
    }
}
