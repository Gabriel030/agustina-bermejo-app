package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.GradeMode;

public record HistoricalSubject(String code, String name, Integer weeklyHours, GradeMode gradeMode,
        Integer maxFinalExamAttempts, boolean active) {
}
