package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;

@NamedInterface("api")
public record CurriculumPlanView(Long id, Long programId, Integer versionNumber, String name,
                                 CurriculumPlanStatus status, LocalDate validFrom, LocalDate validUntil,
                                 LocalDate graduationDeadline, Integer totalYears, Integer totalCreditsRequired,
                                 Integer totalHoursRequired) {
}
