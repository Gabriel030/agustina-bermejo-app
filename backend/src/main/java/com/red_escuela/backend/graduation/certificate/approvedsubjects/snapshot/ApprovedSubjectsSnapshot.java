package com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ApprovedSubjectsSnapshot(
        String holderFullName,
        String documentTypeLabel,
        String documentNumber,
        String programName,
        String institutionName,
        List<ApprovedSubjectLine> subjects,
        int approvedCount,
        int totalSubjects,
        BigDecimal progressPercentage,
        BigDecimal averageGrade,
        RecipientType recipientType,
        String recipientText,
        LocalDate issuedOn,
        LocalDate validUntil,
        List<SignatorySnapshot> signatories,
        String templateRef) {

    public ApprovedSubjectsSnapshot {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        signatories = signatories == null ? List.of() : List.copyOf(signatories);
    }

    public ApprovedSubjectsSnapshot withPublishedTemplate(String institutionName, List<SignatorySnapshot> signatories, String templateRef) {
        return new ApprovedSubjectsSnapshot(holderFullName, documentTypeLabel, documentNumber, programName, institutionName, subjects,
                approvedCount, totalSubjects, progressPercentage, averageGrade, recipientType, recipientText, issuedOn, validUntil,
                signatories, templateRef);
    }
}
