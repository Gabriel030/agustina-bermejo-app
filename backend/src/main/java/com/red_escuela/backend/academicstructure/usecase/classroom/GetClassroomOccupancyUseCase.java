package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomOccupancyResponse;

import java.time.LocalDate;

public interface GetClassroomOccupancyUseCase {
    ClassroomOccupancyResponse getOccupancy(Long classroomId, Long termId, LocalDate from, LocalDate to);
}
