package com.red_escuela.backend.academicstructure.dto;

import java.time.LocalDate;
import java.util.List;

public record ClassroomOccupancyResponse(
        Long classroomId,
        String classroomName,
        Integer capacity,
        Long termId,
        LocalDate from,
        LocalDate to,
        List<ClassroomSlotOccupancyResponse> slots
) {
}
