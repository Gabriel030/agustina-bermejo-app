package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomAvailabilityResponse;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public interface GetClassroomAvailabilityUseCase {
    List<ClassroomAvailabilityResponse> getAvailability(Long termId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime);
}
