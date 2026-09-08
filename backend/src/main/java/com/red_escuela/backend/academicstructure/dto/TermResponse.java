package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;

import java.time.LocalDateTime;

public record TermResponse(
        Long id, Integer year, String description, String type, String status,
        LocalDateTime startDate, LocalDateTime endDate,
        LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd, boolean enrollmentOpen) {

    public static TermResponse from(Term term) {
        return new TermResponse(
                term.getId(), term.getYear(), term.getDescription(), term.getType(),
                term.getStatus() == null ? null : term.getStatus().name(),
                term.getStartDate(), term.getEndDate(), term.getEnrollmentStart(), term.getEnrollmentEnd(),
                calcularEnrollmentOpen(term));
    }

    // Mismo criterio que SubjectSectionEnrollmentService.validarVentanaDeInscripcion (override
    // manual gana, si no hay override se mira la fecha) pero sin tirar excepción — acá el
    // frontend solo necesita un booleano para decidir si mostrar la acción de inscribirse.
    private static boolean calcularEnrollmentOpen(Term term) {
        if (term.getEnrollmentOverride() == TermEnrollmentOverride.CLOSED) {
            return false;
        }
        if (term.getEnrollmentOverride() == TermEnrollmentOverride.OPEN) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean antesDeEmpezar = term.getEnrollmentStart() != null && now.isBefore(term.getEnrollmentStart());
        boolean despuesDeTerminar = term.getEnrollmentEnd() != null && now.isAfter(term.getEnrollmentEnd());
        return !antesDeEmpezar && !despuesDeTerminar;
    }
}
