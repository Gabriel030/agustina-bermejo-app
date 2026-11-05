package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.operation.CollectionReminderPlanningBillingRunStep;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SlowTest(reason = "Runs the full planning job day by day over the shared database in thirteen recovery scenarios")
class ReminderRecoveryTimelineIT extends AbstractReminderTimelineIT {

    @Autowired
    private CollectionReminderPlanningBillingRunStep planningStep;

    private Student student;
    private CourseEnrollment enrollment;
    private Invoice installment;

    private static LocalDate march(int day) {
        return LocalDate.of(2027, 3, day);
    }

    @BeforeEach
    void createStudentWithAnInstallmentDueOnTheTwelfth() {
        jdbc.update("delete from collection_reminder_run");
        student = newStudent();
        Course course = newCourse();
        enrollment = enrollInCourse(student, course);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(march(3), LocalTime.of(4, 0)));
        installment = installmentOf(enrollment, march(3), march(12));
    }

    private void runJobOn(LocalDate... days) {
        for (LocalDate day : days) {
            runJobOn(day);
        }
    }

    private void runJobOn(LocalDate day) {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(day, LocalTime.of(5, 0)));
        BillingRunStepOutcome outcome = planningStep.runStepForBusinessDate(day);
        assertThat(outcome.items()).as("planning errors on " + day).isEmpty();
        assertThat(outcome.failed()).as("planning failures on " + day).isZero();
        Long expected = jdbc.queryForObject("select coalesce(sum(jsonb_array_length(in_app_messages) + jsonb_array_length(email_messages)), 0) from collection_reminder "
                + "where business_date = ? and student_id = ?", Long.class, day, student.getId());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject("select count(*) from notification_delivery "
                + "where scheduled_for = ? and recipient_person_id = ?", Long.class, day, student.getPerson().getId())).as("deliveries of " + day).isEqualTo(expected));
    }

    private void runJobFromTo(LocalDate from, LocalDate toInclusive) {
        for (LocalDate day = from; !day.isAfter(toInclusive); day = day.plusDays(1)) {
            runJobOn(day);
        }
    }

    private long rowsOf(LocalDate day) {
        return jdbc.queryForObject("select count(*) from collection_reminder where student_id = ? and business_date = ?", Long.class, student.getId(), day);
    }

    private String emailTextOf(LocalDate day) {
        List<String> texts = jdbc.queryForList("select email_messages::text from collection_reminder where student_id = ? and business_date = ?", String.class, student.getId(), day);
        return texts.isEmpty() ? "" : texts.get(0);
    }

    private String inAppTextOf(LocalDate day) {
        List<String> texts = jdbc.queryForList("select in_app_messages::text from collection_reminder where student_id = ? and business_date = ?", String.class, student.getId(), day);
        return texts.isEmpty() ? "" : texts.get(0);
    }

    private int emailMessageCountOf(LocalDate day) {
        List<Integer> counts = jdbc.queryForList("select jsonb_array_length(email_messages) from collection_reminder where student_id = ? and business_date = ?", Integer.class,
                student.getId(), day);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    @Test
    @Tag("D-436")
    @Tag("D-437")
    void jobDownOneBusinessDayRecoversTheAfterDueNoticeTheNextMorning() {
        runJobFromTo(march(19), march(21));

        runJobOn(march(23));

        assertThat(rowsOf(march(22))).isZero();
        assertThat(emailTextOf(march(23))).contains("after-due");
        assertThat(inAppTextOf(march(23))).contains("after-due");
    }

    @Test
    @Tag("D-436")
    void jobDownTwoBusinessDaysRecoversTheAfterIssueNoticeBeforeTheDueDate() {
        runJobFromTo(march(3), march(7));

        runJobOn(march(10));

        assertThat(emailTextOf(march(10))).contains("after-issue").doesNotContain("before-due");
    }

    @Test
    @Tag("D-436")
    @Tag("D-437")
    void jobDownThreeBusinessDaysSendsTheRecoveredAndTheTodayNoticeInASingleMail() {
        runJobFromTo(march(3), march(7));

        runJobOn(march(11));

        assertThat(emailMessageCountOf(march(11))).isEqualTo(1);
        assertThat(emailTextOf(march(11))).contains("after-issue").contains("before-due");
    }

    @Test
    @Tag("D-436")
    void aNoticeOlderThanThreeBusinessDaysIsNotRecovered() {
        runJobFromTo(march(3), march(7));

        runJobOn(march(12));

        assertThat(emailTextOf(march(12))).contains("before-due").doesNotContain("after-issue");
        assertThat(inAppTextOf(march(12))).doesNotContain("after-issue");
    }

    @Test
    @Tag("D-436")
    void aMissedBeforeDueNoticeIsDiscardedOnceTheDueDateHasPassed() {
        runJobFromTo(march(3), march(10));

        runJobOn(march(15));

        assertThat(rowsOf(march(15))).isZero();
    }

    @Test
    @Tag("D-436")
    @Tag("D-346")
    void aPaidInstallmentIsNotRecovered() {
        runJobFromTo(march(19), march(21));
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(march(22), LocalTime.of(3, 0)));
        pay(installment, march(22));

        runJobOn(march(23));

        assertThat(rowsOf(march(23))).isZero();
    }

    @Test
    @Tag("D-436")
    @Tag("D-346")
    void anInstallmentWithAReceiptUnderReviewIsNotRecovered() {
        runJobFromTo(march(19), march(21));
        uploadReceipt(installment);

        runJobOn(march(23));

        assertThat(emailTextOf(march(23))).doesNotContain("after-due");
        assertThat(inAppTextOf(march(23))).doesNotContain("after-due");
    }

    @Test
    @Tag("D-437")
    void runningTheJobTwiceTheSameMorningDoesNotRepeatTheRecoveredNotice() {
        runJobFromTo(march(19), march(21));
        runJobOn(march(23));

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(march(23), LocalTime.of(5, 30)));
        planningStep.runStepForBusinessDate(march(23));

        assertThat(rowsOf(march(23))).isEqualTo(1);
        assertThat(emailMessageCountOf(march(23))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where scheduled_for = ? and recipient_person_id = ? and channel = 'EMAIL'", Long.class,
                march(23), student.getPerson().getId())).isEqualTo(1);
    }

    @Test
    @Tag("D-437")
    void theNextRunAfterTheRecoveryDoesNotRepeatTheNotice() {
        runJobFromTo(march(19), march(21));
        runJobOn(march(23));

        runJobOn(march(24));

        assertThat(rowsOf(march(24))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from collection_reminder_run where business_date between ? and ?", Long.class, march(19), march(24))).isEqualTo(6);
    }

    @Test
    @Tag("D-437")
    void aDayThatAlreadyProducedItsRowIsNotSentAgainWhenItsRunWasNeverRecorded() {
        runJobFromTo(march(19), march(22));
        assertThat(emailTextOf(march(22))).contains("after-due");
        jdbc.update("delete from collection_reminder_run where business_date = ?", march(22));

        runJobOn(march(23));

        assertThat(rowsOf(march(23))).isZero();
    }

    @Test
    @Tag("D-437")
    void severalMissedDaysProduceASingleMailWithEveryRecoveredNotice() {
        Course course = enrollment.getCourse();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(march(3), LocalTime.of(12, 0)));
        publishCourseSchedule(course, march(4), List.of(
                notice("after-due-a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 3, EnumSet.allOf(ReminderChannel.class), null),
                notice("after-due-b", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 4, EnumSet.allOf(ReminderChannel.class), null)));
        runJobFromTo(march(12), march(14));

        runJobOn(march(17));

        assertThat(emailMessageCountOf(march(17))).isEqualTo(1);
        assertThat(emailTextOf(march(17))).contains("after-due-a").contains("after-due-b");
        assertThat(inAppTextOf(march(17))).contains("after-due-a").contains("after-due-b");
    }

    @Test
    @Tag("D-438")
    void aRecoveredMailKeepsTheDispatchPriorityOfItsNoticeAndIsQueuedForTheSameDispatch() {
        runJobFromTo(march(19), march(21));

        runJobOn(march(23));

        Integer priority = jdbc.queryForObject("select (email_messages -> 0 ->> 'priority')::int from collection_reminder where student_id = ? and business_date = ?", Integer.class,
                student.getId(), march(23));
        assertThat(priority).isEqualTo(PlannedReminderNoticeType.AFTER_DUE_REMINDER.dispatchPriority());
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where scheduled_for = ? and recipient_person_id = ? and channel = 'EMAIL' and priority = ?",
                Long.class, march(23), student.getPerson().getId(), priority)).isEqualTo(1);
    }

    @Test
    @Tag("D-436")
    void withoutAnyRecordedRunNothingIsRecoveredOnTheFirstRun() {
        runJobOn(march(23));

        assertThat(rowsOf(march(23))).isZero();
    }
}
