package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.InstructorRole;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.SubjectSectionInstructor;

import java.time.LocalDateTime;
import java.util.List;

public record SectionEnrollmentResponse(
        Long id, Long programEnrollmentId, Long sectionId, String sectionName, Long subjectId, String subjectCode, String subjectName,
        Integer curricularYear, String status, Integer attemptNumber, Double finalScore, String finalGradeType,
        LocalDateTime enrolledAt, LocalDateTime droppedAt, String schedule, String instructorTitular, String instructorAdjunto) {

    // curricularYear no vive en SubjectSectionEnrollment ni en SubjectSection — es propio del
    // plan de UNA carrera (ProgramSubject.curricularYear, ver comentario de SubjectSection: una
    // materia se comparte entre carreras, así que su año curricular depende de en cuál estás
    // parado). Los callers que no necesitan mostrar el año (ej. confirmar una inscripción)
    // usan el overload sin ese dato en vez de pagar el lookup.
    public static SectionEnrollmentResponse from(SubjectSectionEnrollment enrollment) {
        return from(enrollment, null);
    }

    public static SectionEnrollmentResponse from(SubjectSectionEnrollment enrollment, Integer curricularYear) {
        // instructorTitular/instructorAdjunto: primer LEAD y primer ASSISTANT asignados a la
        // comisión (una comisión puede tener varios de cada rol — Épica 2 Fase 1 solo necesita
        // mostrar "el" profesor y "el" adjunto en la grilla del alumno, no la lista completa).
        String titular = nombrePorRol(enrollment, InstructorRole.LEAD);
        String adjunto = nombrePorRol(enrollment, InstructorRole.ASSISTANT);
        return new SectionEnrollmentResponse(
                enrollment.getId(),
                enrollment.getProgramEnrollment().getId(),
                enrollment.getSubjectSection().getId(),
                enrollment.getSubjectSection().getName(),
                enrollment.getSubjectSection().getSubject().getId(),
                enrollment.getSubjectSection().getSubject().getCode(),
                enrollment.getSubjectSection().getSubject().getName(),
                curricularYear,
                enrollment.getStatus() == null ? null : enrollment.getStatus().name(),
                enrollment.getAttemptNumber(),
                enrollment.getFinalScore(),
                enrollment.getFinalGradeType() == null ? null : enrollment.getFinalGradeType().name(),
                enrollment.getEnrolledAt(),
                enrollment.getDroppedAt(),
                enrollment.getSubjectSection().getSchedule(),
                titular,
                adjunto);
    }

    private static String nombrePorRol(SubjectSectionEnrollment enrollment, InstructorRole role) {
        List<SubjectSectionInstructor> instructors = enrollment.getSubjectSection().getInstructors();
        if (instructors == null) {
            return null;
        }
        return instructors.stream()
                .filter(instructor -> instructor.getRole() == role)
                .findFirst()
                .map(SubjectSectionInstructor::getInstructor)
                .map(instructor -> instructor.getPerson().getName() + " " + instructor.getPerson().getLastname())
                .orElse(null);
    }
}
