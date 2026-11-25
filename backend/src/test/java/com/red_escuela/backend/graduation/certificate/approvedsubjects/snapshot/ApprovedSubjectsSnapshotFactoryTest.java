package com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsGradeUnavailableException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedSubjectsSnapshotFactoryTest {

    private final ApprovedSubjectsSnapshotFactory factory = new ApprovedSubjectsSnapshotFactory();

    private ApprovedSubjectsSnapshotFactory.Input input(List<ApprovedSubjectLine> approved, int total, ApprovedSubjectsRecipient recipient) {
        return inputWithValidity(approved, total, recipient, 30);
    }

    private ApprovedSubjectsSnapshotFactory.Input inputWithValidity(List<ApprovedSubjectLine> approved, int total,
                                                                    ApprovedSubjectsRecipient recipient, int validityDays) {
        return new ApprovedSubjectsSnapshotFactory.Input("PÉREZ, ANA", "DNI", "30123456", "Tecnicatura en Enfermería",
                "Instituto de prueba", approved, total, recipient, LocalDate.of(2026, 11, 5), validityDays,
                List.of(new SignatorySnapshot("Dirección", "Dirección académica")));
    }

    private ApprovedSubjectLine line(String name, int year, String grade) {
        return new ApprovedSubjectLine(name, year, grade == null ? null : new BigDecimal(grade), LocalDate.of(2026, 7, 1));
    }

    @Test
    void computesAverageAndProgressPercentage() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(
                List.of(line("Anatomía", 1, "8"), line("Fisiología", 1, "9"), line("Ética", 2, "7.5")), 8, null));

        assertThat(snapshot.averageGrade()).isEqualByComparingTo("8.17");
        assertThat(snapshot.approvedCount()).isEqualTo(3);
        assertThat(snapshot.totalSubjects()).isEqualTo(8);
        assertThat(snapshot.progressPercentage()).isEqualByComparingTo("37.50");
    }

    @Test
    void ignoresSubjectsWithoutGradeInTheAverage() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(List.of(line("Anatomía", 1, "8"), line("Ética", 1, null)), 2, null));

        assertThat(snapshot.averageGrade()).isEqualByComparingTo("8.00");
        assertThat(snapshot.approvedCount()).isEqualTo(2);
    }

    @Test
    void averageIsNullWhenNothingHasGrade() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(List.of(), 5, null));

        assertThat(snapshot.averageGrade()).isNull();
        assertThat(snapshot.progressPercentage()).isEqualByComparingTo("0");
    }

    @Test
    void zeroTotalSubjectsGivesZeroProgress() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(List.of(), 0, null));

        assertThat(snapshot.progressPercentage()).isEqualByComparingTo("0");
    }

    @Test
    void ordersSubjectsByYearThenName() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(
                List.of(line("Ética", 2, "7"), line("fisiología", 1, "9"), line("Anatomía", 1, "8")), 3, null));

        assertThat(snapshot.subjects()).extracting(ApprovedSubjectLine::name).containsExactly("Anatomía", "fisiología", "Ética");
    }

    @Test
    void validUntilIsThirtyDaysAfterIssue() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(List.of(), 1, null));

        assertThat(snapshot.validUntil()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(snapshot.templateRef()).isEqualTo(ApprovedSubjectsSnapshotFactory.TEMPLATE_REF);
    }

    @Test
    void validUntilFollowsTheConfiguredValidityDays() {
        assertThat(factory.create(inputWithValidity(List.of(), 1, null, 60)).validUntil()).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(factory.create(inputWithValidity(List.of(), 1, null, 1)).validUntil()).isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    void legacyGradeThatCannotBeRepresentedRejectsTheIssuanceNamingTheSubject() {
        assertThatThrownBy(() -> factory.create(input(List.of(line("Anatomía", 1, "8"), line("Ética", 2, "11")), 2, null)))
                .isInstanceOfSatisfying(ApprovedSubjectsGradeUnavailableException.class,
                        e -> assertThat(e.details()).containsEntry("subjectName", "Ética"));
        assertThatThrownBy(() -> factory.create(input(List.of(line("Anatomía", 1, "8.123")), 1, null)))
                .isInstanceOf(ApprovedSubjectsGradeUnavailableException.class);
    }

    @Test
    void keepsRecipientLiterally() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(List.of(), 1,
                new ApprovedSubjectsRecipient(RecipientType.EMPLOYER, "  Acme <S.A.> & Cía  ")));

        assertThat(snapshot.recipientType()).isEqualTo(RecipientType.EMPLOYER);
        assertThat(snapshot.recipientText()).isEqualTo("  Acme <S.A.> & Cía  ");
    }

    @Test
    void rejectsRecipientTextWithLineBreaks() {
        assertThatThrownBy(() -> new ApprovedSubjectsRecipient(RecipientType.OTHER, "uno\ndos"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovedSubjectsRecipient(RecipientType.OTHER, "uno\rdos"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRecipientTextLongerThanEighty() {
        assertThat(new ApprovedSubjectsRecipient(RecipientType.OTHER, "a".repeat(80)).text()).hasSize(80);
        assertThatThrownBy(() -> new ApprovedSubjectsRecipient(RecipientType.OTHER, "a".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRecipientWithoutTypeOrText() {
        assertThatThrownBy(() -> new ApprovedSubjectsRecipient(null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovedSubjectsRecipient(RecipientType.OTHER, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subjectsWithoutYearGoLastAndEqualNamesKeepInputOrder() {
        ApprovedSubjectLine first = new ApprovedSubjectLine("Ética", 1, new BigDecimal("7"), LocalDate.of(2026, 7, 1));
        ApprovedSubjectLine second = new ApprovedSubjectLine("Ética", 1, new BigDecimal("9"), LocalDate.of(2026, 8, 1));
        ApprovedSubjectLine withoutYear = new ApprovedSubjectLine("Anatomía", null, new BigDecimal("8"), LocalDate.of(2026, 6, 1));

        ApprovedSubjectsSnapshot snapshot = factory.create(input(List.of(withoutYear, first, second), 3, null));

        assertThat(snapshot.subjects()).containsExactly(first, second, withoutYear);
    }

    @Test
    void borderGradesAndFullProgressAreAccepted() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(List.of(line("Anatomía", 1, "1"), line("Ética", 1, "10")), 2, null));

        assertThat(snapshot.averageGrade()).isEqualByComparingTo("5.50");
        assertThat(snapshot.progressPercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void zeroTotalSubjectsYieldsZeroProgress() {
        ApprovedSubjectsSnapshot snapshot = factory.create(input(List.of(), 0, null));

        assertThat(snapshot.progressPercentage()).isEqualByComparingTo("0.00");
        assertThat(snapshot.averageGrade()).isNull();
    }

    @Test
    void gradesOutsideTheValidRangeAreRejected() {
        assertThatThrownBy(() -> factory.create(input(List.of(line("Anatomía", 1, "0.5")), 1, null)))
                .isInstanceOf(ApprovedSubjectsGradeUnavailableException.class);
        assertThatThrownBy(() -> factory.create(input(List.of(line("Anatomía", 1, "10.01")), 1, null)))
                .isInstanceOf(ApprovedSubjectsGradeUnavailableException.class);
    }
}
