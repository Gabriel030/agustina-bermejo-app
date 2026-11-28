package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectStandingTest {

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }

    private static CourseworkEntry approved(String score, Long enrollmentId, LocalDate date) {
        return new CourseworkEntry(EnrollmentStatus.APPROVED, grade(score), enrollmentId, date);
    }

    @Test
    void mostRecentApprovalByDateDecidesTheScore() {
        SubjectStanding standing = new SubjectStanding(List.of(
                approved("9", 20L, LocalDate.of(2025, 7, 1)), approved("6", 10L, LocalDate.of(2026, 7, 1))), null);

        assertThat(standing.approvedScore()).isEqualTo(grade("6"));
        assertThat(standing.scoreForPrerequisiteCheck()).isEqualTo(grade("6"));
    }

    @Test
    void enrollmentIdBreaksATieBetweenApprovalsOnTheSameDate() {
        LocalDate sameDay = LocalDate.of(2026, 7, 1);
        SubjectStanding standing = new SubjectStanding(List.of(approved("9", 10L, sameDay), approved("5", 11L, sameDay)), null);

        assertThat(standing.approvedScore()).isEqualTo(grade("5"));
    }

    @Test
    void approvalWithoutDateLosesAgainstADatedApproval() {
        SubjectStanding standing = new SubjectStanding(List.of(
                approved("9", 30L, null), approved("6", 10L, LocalDate.of(2024, 1, 1))), null);

        assertThat(standing.approvedScore()).isEqualTo(grade("6"));
    }

    @Test
    void failedAttemptsAreIgnored() {
        SubjectStanding standing = new SubjectStanding(List.of(
                new CourseworkEntry(EnrollmentStatus.FAILED, grade("2"), 50L, LocalDate.of(2027, 1, 1)),
                approved("7", 10L, LocalDate.of(2026, 1, 1))), null);

        assertThat(standing.approvedScore()).isEqualTo(grade("7"));
    }

    @Test
    void noApprovalHasNoScore() {
        assertThat(SubjectStanding.NONE.approvedScore()).isNull();
        assertThat(SubjectStanding.NONE.scoreForPrerequisiteCheck()).isNull();
    }
}
