package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Subject;

public record SubjectResponse(
        Long id, String code, String name, String description,
        Integer weeklyHours, Integer weeklyHoursLab, String gradeMode,
        Integer maxFinalExamAttempts, String status, String rejectionReason) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(
                subject.getId(), subject.getCode(), subject.getName(), subject.getDescription(),
                subject.getWeeklyHours(), subject.getWeeklyHoursLab(),
                subject.getGradeMode() == null ? null : subject.getGradeMode().name(),
                subject.getMaxFinalExamAttempts(),
                subject.getStatus() == null ? null : subject.getStatus().name(),
                subject.getRejectionReason());
    }
}
