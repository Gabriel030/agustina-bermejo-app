package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.GradeMode;

public record UpdateSubjectRequest(
        String name, String description,
        Integer weeklyHours, Integer weeklyHoursLab,
        GradeMode gradeMode, Integer maxFinalExamAttempts) {
}
