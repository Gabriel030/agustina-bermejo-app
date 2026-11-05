package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Runs the daily reminder jobs for a week before checking the re-verification of the dispatch, two scenarios in one class")
class EmailDispatchTimelineIT extends AbstractReminderTimelineIT {

    private static LocalDate march(int day) {
        return LocalDate.of(2027, 3, day);
    }

    private List<Map<String, Object>> emailsOf(Student student) {
        return jdbc.queryForList("select status, status_reason from notification_delivery where recipient_person_id = ? and channel = 'EMAIL' order by id",
                student.getPerson().getId());
    }

    private ReminderTimelineHarness harnessWithAnInstallmentDueOnTheTwelfth(Student student) {
        ReminderTimelineHarness harness = newHarness();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(march(3))) {
                installmentOf(enrollment, march(3), march(12));
            }
        });
        harness.run(march(1), march(7));
        return harness;
    }

    @Test
    @Tag("D-354")
    void anInstallmentPaidAtHalfPastEightIsNotMailedAtNine() {
        Student student = newStudent();
        ReminderTimelineHarness harness = harnessWithAnInstallmentDueOnTheTwelfth(student);

        harness.runDayUntilPlanned(march(8));
        assertThat(emailsOf(student)).hasSize(1);
        assertThat(emailsOf(student).get(0)).containsEntry("status", "QUEUED");
        harness.atTime(march(8), 8, 30);
        pay(lastInvoiceOf(student), march(8));
        harness.dispatchAt(march(8), 9, 0);

        assertThat(emailsOf(student)).hasSize(1);
        assertThat(emailsOf(student).get(0)).containsEntry("status", "SUPPRESSED").containsEntry("status_reason", "NO_LONGER_APPLICABLE");
        assertThat(harness.sentEmailDaysOf(student)).isEmpty();
    }

    @Test
    @Tag("D-354")
    void anInstallmentStillOwedAtNineIsMailedInTheSameScenario() {
        Student student = newStudent();
        ReminderTimelineHarness harness = harnessWithAnInstallmentDueOnTheTwelfth(student);

        harness.runDayUntilPlanned(march(8));
        harness.dispatchAt(march(8), 9, 0);

        assertThat(emailsOf(student)).hasSize(1);
        assertThat(emailsOf(student).get(0)).containsEntry("status", "SENT_SIMULATED");
    }
}
