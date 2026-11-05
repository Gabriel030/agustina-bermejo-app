package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomOccupancyResponse;
import com.red_escuela.backend.academicstructure.service.ClassroomOccupancyReader;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;

@UseCase
public class GetClassroomOccupancyService implements GetClassroomOccupancyUseCase {

    private final ClassroomOccupancyReader occupancyReader;

    public GetClassroomOccupancyService(ClassroomOccupancyReader occupancyReader) {
        this.occupancyReader = occupancyReader;
    }

    @Override
    public ClassroomOccupancyResponse getOccupancy(Long classroomId, Long termId, LocalDate from, LocalDate to) {
        return occupancyReader.readOccupancy(classroomId, termId, from, to);
    }
}
