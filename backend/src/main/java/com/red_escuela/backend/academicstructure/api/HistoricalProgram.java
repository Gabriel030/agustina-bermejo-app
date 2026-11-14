package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.ProgramLevel;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.shared.domain.Money;

public record HistoricalProgram(String name, ProgramLevel level, Integer totalYears, int totalCreditsRequired,
        Integer totalHours, TeachingMode teachingMode, boolean active, Money tuition, Money enrollmentFee,
        String graduationProfile) {
}
