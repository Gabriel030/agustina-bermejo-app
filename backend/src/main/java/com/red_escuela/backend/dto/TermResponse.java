package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Term;

import java.time.LocalDateTime;

public record TermResponse(
        Long id, Integer year, String description, String type, String status,
        LocalDateTime startDate, LocalDateTime endDate,
        LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) {

    public static TermResponse from(Term term) {
        return new TermResponse(
                term.getId(), term.getYear(), term.getDescription(), term.getType(),
                term.getStatus() == null ? null : term.getStatus().name(),
                term.getStartDate(), term.getEndDate(), term.getEnrollmentStart(), term.getEnrollmentEnd());
    }
}
