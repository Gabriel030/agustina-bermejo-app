package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Course;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CourseResponse {
    private final Long id;
    private final String name;
    private final Long termId;
    private final String schedule;
    private final Integer capacity;
    private final BigDecimal price;
    private final Integer durationMonths;
    private final String status;
    private final boolean visibleInCatalog;
    private final String teachingMode;
    private final LocalDateTime registrationStart;
    private final LocalDateTime registrationEnd;

    public static CourseResponse from(Course course) {
        return CourseResponse.builder()
                .id(course.getId())
                .name(course.getName())
                .termId(course.getTerm().getId())
                .schedule(course.getSchedule())
                .capacity(course.getCapacity())
                .price(course.getPrice())
                .durationMonths(course.getDurationMonths())
                .status(course.getIsActive() == null ? null : course.getIsActive().name())
                .visibleInCatalog(course.isVisibleInCatalog())
                .teachingMode(course.getTeachingMode() == null ? null : course.getTeachingMode().name())
                .registrationStart(course.getRegistrationStart())
                .registrationEnd(course.getRegistrationEnd())
                .build();
    }
}
