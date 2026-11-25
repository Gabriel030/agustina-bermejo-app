package com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot;

import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class ApprovedSubjectsSnapshotFactory {

    public static final String TEMPLATE_REF = "APPROVED_SUBJECTS_BUILT_IN_V1";
    public static final int VALIDITY_DAYS = 30;

    public record Input(
            String holderFullName,
            String documentTypeLabel,
            String documentNumber,
            String programName,
            String institutionName,
            List<ApprovedSubjectLine> approved,
            int totalSubjects,
            ApprovedSubjectsRecipient recipient,
            LocalDate issuedOn,
            List<SignatorySnapshot> signatories) {
    }

    public ApprovedSubjectsSnapshot create(Input input) {
        List<ApprovedSubjectLine> ordered = input.approved().stream()
                .sorted(Comparator.comparing(ApprovedSubjectLine::year, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ApprovedSubjectLine::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Grade> grades = ordered.stream()
                .map(ApprovedSubjectLine::grade)
                .filter(Objects::nonNull)
                .map(Grade::of)
                .toList();
        BigDecimal average = Grade.average(grades).map(Grade::value).orElse(null);
        BigDecimal progress = Percentage.ratio(ordered.size(), input.totalSubjects())
                .map(Percentage::points)
                .orElse(BigDecimal.ZERO.setScale(2));
        return new ApprovedSubjectsSnapshot(
                input.holderFullName(),
                input.documentTypeLabel(),
                input.documentNumber(),
                input.programName(),
                input.institutionName(),
                ordered,
                ordered.size(),
                input.totalSubjects(),
                progress,
                average,
                input.recipient() == null ? null : input.recipient().type(),
                input.recipient() == null ? null : input.recipient().text(),
                input.issuedOn(),
                input.issuedOn().plusDays(VALIDITY_DAYS),
                input.signatories(),
                TEMPLATE_REF);
    }
}
