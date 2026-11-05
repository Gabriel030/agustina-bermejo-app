package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.Grade;
import java.math.BigDecimal;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;

import java.time.LocalDateTime;
import java.util.List;

public record SectionEnrollmentResponse(
        Long id, Long programEnrollmentId, Long sectionId, String sectionName, Long subjectId, String subjectCode, String subjectName,
        Integer curricularYear, String curricularTerm, String status, Integer attemptNumber, BigDecimal finalScore, String finalGradeType,
        LocalDateTime enrolledAt, LocalDateTime droppedAt, String schedule, String instructorTitular, String instructorAdjunto) {
    public static SectionEnrollmentResponse from(SubjectSectionEnrollment enrollment) {
        return from(enrollment, null, null);
    }

    public static SectionEnrollmentResponse from(SubjectSectionEnrollment enrollment, Integer curricularYear) {
        return from(enrollment, curricularYear, null);
    }

    public static SectionEnrollmentResponse from(
            SubjectSectionEnrollment enrollment, Integer curricularYear, CurricularTerm curricularTerm) {
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
                curricularTerm == null ? null : curricularTerm.name(),
                enrollment.getStatus() == null ? null : enrollment.getStatus().name(),
                enrollment.getAttemptNumber(),
                Grade.valueOrNull(enrollment.getFinalScore()),
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
