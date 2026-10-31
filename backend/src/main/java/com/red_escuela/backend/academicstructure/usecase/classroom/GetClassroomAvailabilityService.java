package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomAvailabilityResponse;
import com.red_escuela.backend.academicstructure.service.ClassroomAvailabilityReader;
import com.red_escuela.backend.shared.UseCase;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@UseCase
public class GetClassroomAvailabilityService implements GetClassroomAvailabilityUseCase {

    private final ClassroomAvailabilityReader availabilityReader;

    public GetClassroomAvailabilityService(ClassroomAvailabilityReader availabilityReader) {
        this.availabilityReader = availabilityReader;
    }

    @Override
    public List<ClassroomAvailabilityResponse> getAvailability(Long termId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        return availabilityReader.readAvailability(termId, dayOfWeek, startTime, endTime);
    }
}
