package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.module.CatalogPriced;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
public class CourseResponse implements CatalogPriced<CourseResponse> {
    private final Long id;
    private final String name;
    private final Long termId;
    private final String schedule;
    private final Integer capacity;
    private final Money price;
    private final Integer durationMonths;
    private final String status;
    private final boolean visibleInCatalog;
    private final String area;
    private final LocalDateTime catalogPublishedAt;
    private final String teachingMode;
    private final LocalDateTime registrationStart;
    private final LocalDateTime registrationEnd;
    private final boolean registrationOpen;
    private final Boolean full;

    @Override
    public CourseResponse withoutPrices() {
        return toBuilder().price(null).build();
    }

    public static CourseResponse from(Course course, long occupiedSeats) {
        return from(course, course.priceAsMoney(), occupiedSeats);
    }

    public static CourseResponse from(Course course) {
        return from(course, course.priceAsMoney());
    }

    public static CourseResponse from(Course course, Money price, long occupiedSeats) {
        return baseBuilder(course, price).full(!course.hasCapacityFor(occupiedSeats)).build();
    }

    public static CourseResponse from(Course course, Money price) {
        return baseBuilder(course, price).build();
    }

    private static CourseResponseBuilder baseBuilder(Course course, Money price) {
        return CourseResponse.builder()
                .id(course.getId())
                .name(course.getName())
                .termId(course.getTerm().getId())
                .schedule(course.getSchedule())
                .capacity(course.getCapacity())
                .price(price)
                .durationMonths(course.getDurationMonths())
                .status(course.getIsActive() == null ? null : course.getIsActive().name())
                .visibleInCatalog(course.isVisibleInCatalog())
                .area(course.getCatalogArea() == null ? null : course.getCatalogArea().name())
                .catalogPublishedAt(course.getCatalogPublishedAt())
                .teachingMode(course.getTeachingMode() == null ? null : course.getTeachingMode().name())
                .registrationStart(course.getRegistrationStart())
                .registrationEnd(course.getRegistrationEnd())
                .registrationOpen(course.isRegistrationOpenAt(LocalDateTime.now()));
    }
}
