package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.module.CatalogPriced;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;


@Getter
@Builder(toBuilder = true)
public class ProgramResponse implements CatalogPriced<ProgramResponse> {
    private final Long id;
    private final String name;
    private final Integer totalYears;
    private final Money price;
    private final Money enrollmentFeeAmount;
    private final String status;
    private final Integer totalCreditsRequired;
    private final Integer totalHoursRequired;
    private final boolean visibleInCatalog;
    private final String area;
    private final LocalDateTime catalogPublishedAt;
    private final String teachingMode;
    private final String level;
    private final String graduationProfile;
    private final String admissionRequirements;

    @Override
    public ProgramResponse withoutPrices() {
        return toBuilder().price(null).enrollmentFeeAmount(null).build();
    }

    public static ProgramResponse from(Program program) {
        return from(program, program.priceAsMoney(), program.enrollmentFeeAsMoney());
    }

    public static ProgramResponse from(Program program, Money price, Money enrollmentFeeAmount) {
        return ProgramResponse.builder()
                .id(program.getId())
                .name(program.getName())
                .totalYears(program.getTotalYears())
                .price(price)
                .enrollmentFeeAmount(enrollmentFeeAmount)
                .status(program.getStatus() == null ? null : program.getStatus().name())
                .totalCreditsRequired(program.getTotalCreditsRequired())
                .totalHoursRequired(program.getTotalHoursRequired())
                .visibleInCatalog(program.isVisibleInCatalog())
                .area(program.getCatalogArea() == null ? null : program.getCatalogArea().name())
                .catalogPublishedAt(program.getCatalogPublishedAt())
                .teachingMode(program.getTeachingMode() == null ? null : program.getTeachingMode().name())
                .level(program.getLevel() == null ? null : program.getLevel().name())
                .graduationProfile(program.getGraduationProfile())
                .admissionRequirements(program.getAdmissionRequirements())
                .build();
    }
}
