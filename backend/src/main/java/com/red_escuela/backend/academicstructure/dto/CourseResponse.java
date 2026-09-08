package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Course;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseResponse(
        Long id, String name, Long termId, String schedule, Integer capacity,
        BigDecimal price, Integer durationMonths, String status,
        LocalDateTime registrationStart, LocalDateTime registrationEnd) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(), course.getName(), course.getTerm().getId(), course.getSchedule(),
                course.getCapacity(), course.getPrice(), course.getDurationMonths(),
                course.getIsActive() == null ? null : course.getIsActive().name(),
                course.getRegistrationStart(), course.getRegistrationEnd());
    }
}
