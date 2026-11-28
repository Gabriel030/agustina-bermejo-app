package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.attendance.domain.AttendanceDecisionRevertWindow;
import com.red_escuela.backend.billing.planning.service.MonthlyInstallmentDueDateCalculator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineBoundariesReproductionTest {

    @Test
    @Disabled("E34-B-P5")
    void courseWaitlistIsStillValidOnTheAnnouncedDeadlineDay() {
        Term term = new Term();
        term.setStartDate(LocalDateTime.of(2027, 3, 1, 0, 0));
        Course course = new Course();
        course.setTerm(term);
        course.setWaitlistDeadlineOffsetDays(3);
        LocalDate announcedDeadline = course.waitlistDeadline();

        assertThat(announcedDeadline).isEqualTo(LocalDate.of(2027, 3, 4));
        assertThat(course.waitlistDeadlineHasPassed(announcedDeadline)).isFalse();
        assertThat(course.waitlistDeadlineHasPassed(announcedDeadline.plusDays(1))).isTrue();
    }

    @Test
    void installmentDueDateNeverFallsOnWeekend() {
        LocalDate issueDate = LocalDate.of(2027, 3, 1);

        LocalDate dueDate = new MonthlyInstallmentDueDateCalculator().calculateInstallmentDueDate(issueDate, 5);

        assertThat(issueDate.plusDays(5).getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(dueDate.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(dueDate).isEqualTo(LocalDate.of(2027, 3, 8));
    }

    @Test
    @Disabled("E34-B-P5")
    void attendanceDecisionRevertWindowSpansExactlySevenCalendarDays() {
        LocalDateTime decidedAt = LocalDateTime.of(2027, 3, 1, 10, 0);
        LocalDate decisionDay = decidedAt.toLocalDate();

        assertThat(AttendanceDecisionRevertWindow.isOpen(decidedAt, decisionDay.plusDays(6))).isTrue();
        assertThat(AttendanceDecisionRevertWindow.isOpen(decidedAt, decisionDay.plusDays(7))).isFalse();
    }
}
