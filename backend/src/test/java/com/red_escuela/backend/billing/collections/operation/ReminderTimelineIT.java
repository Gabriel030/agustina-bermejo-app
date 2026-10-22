package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Runs 31-day timelines of the reminder jobs day by day, twelve scenarios in one class")
class ReminderTimelineIT extends AbstractReminderTimelineIT {

    @Autowired
    private ConfigurationVersionRepository configurationVersionRepository;
    @Autowired
    private BillingConfigurationReader configurationReader;

    private static LocalDate march(int day) {
        return LocalDate.of(2027, 3, day);
    }

    private void assertBaseInstallmentTimeline() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(march(3))) {
                installmentOf(enrollment, march(3), march(12));
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(3), march(8), march(11), march(22));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(8), march(11), march(22));
        assertThat(harness.sentEmailDaysOf(student)).containsExactly(march(8), march(11), march(22));
    }

    @Test
    @Tag("D-368")
    @Tag("D-353")
    void baseInstallmentTimeline() {
        assertBaseInstallmentTimeline();
    }

    @Test
    @Tag("D-368")
    @Tag("D-353")
    void baseInstallmentTimelineWithTheJvmDefaultZoneInUtc() {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            assertBaseInstallmentTimeline();
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    @Tag("D-368")
    @Tag("D-346")
    void paidOnTheEighthStopsAfterTheSixth() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(march(3))) {
                installmentOf(enrollment, march(3), march(12));
            }
        }).during(day -> {
            if (day.equals(march(8))) {
                pay(lastInvoiceOf(student), day);
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(3), march(8));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(8));
    }

    @Test
    @Tag("D-368")
    @Tag("D-342")
    void enrollmentFeeDueOnSaturdayRealCalendar() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        ProgramEnrollment enrollment = enrollInProgram(student);
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                enrollmentFeeOf(enrollment, march(1), march(20));
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(1), march(6), march(19), march(30));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(8), march(19), march(30));
    }

    @Test
    @Tag("D-368")
    @Tag("D-342")
    void realCalendarMovesOnlyTheEmail() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                installmentOf(enrollment, march(1), march(11));
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(1), march(6), march(10), march(21));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(8), march(10), march(22));
    }

    @Test
    @Tag("D-368")
    @Tag("D-288")
    void graceDoesNotMoveTheAfterDueNotice() {
        ReminderTimelineHarness harness = newHarness();
        int scenarioGraceDays = 12;
        assertThat(configurationReader.findLatePaymentSettingsEffectiveOn(marchOf2038(1)).map(terms -> terms.gracePeriodDays()).orElse(-1)).isNotEqualTo(scenarioGraceDays);
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, newPerson("direccion", "DIRECTOR", true), scenarioGraceDays, new BigDecimal("0.01"), 90, 99,
                LocalDate.of(2038, 2, 27));
        assertThat(configurationReader.findLatePaymentSettingsEffectiveOn(marchOf2038(1)).map(terms -> terms.gracePeriodDays()).orElse(-1)).isEqualTo(scenarioGraceDays);
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(marchOf2038(1))) {
                installmentOf(enrollment, marchOf2038(1), marchOf2038(11));
            }
        });

        harness.run(marchOf2038(1), marchOf2038(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(marchOf2038(1), marchOf2038(6), marchOf2038(10), marchOf2038(21));
        assertThat(harness.emailDaysOf(student)).containsExactly(marchOf2038(8), marchOf2038(10), marchOf2038(22));
    }

    private static LocalDate marchOf2038(int day) {
        return LocalDate.of(2038, 3, day);
    }

    @Test
    @Tag("D-346")
    void receiptUnderReviewSilencesWithoutRecovery() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                installmentOf(enrollment, march(1), march(11));
            }
        }).during(day -> {
            if (day.equals(march(15))) {
                uploadReceipt(lastInvoiceOf(student));
            }
            if (day.equals(march(26))) {
                rejectReceipt(lastReceiptOf(student));
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(1), march(6), march(10));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(8), march(10));
        assertThat(jdbc.queryForObject("select count(*) from collection_reminder where student_id = ? and business_date = ? and suppressed::text like '%RECEIPT_UNDER_REVIEW%'",
                Long.class, student.getId(), march(21))).isEqualTo(1L);
    }

    @Test
    @Tag("D-094")
    @Tag("D-354")
    void chargebackBringsBackTheAfterDueNotice() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                installmentOf(enrollment, march(1), march(11));
            }
        }).during(day -> {
            if (day.equals(march(5))) {
                pay(lastInvoiceOf(student), day);
            }
            if (day.equals(march(20))) {
                revertPayment(lastInvoiceOf(student), day, 5);
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(1), march(21));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(22));
    }

    @Test
    @Tag("D-350")
    void nextDayVersionChangesTheBeforeDueDate() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        Course course = newCourse();
        CourseEnrollment enrollment = enrollInCourse(student, course);
        publishCourseSchedule(course, LocalDate.of(2027, 2, 27), List.of(
                notice("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1, EnumSet.allOf(ReminderChannel.class), null)));
        publishCourseSchedule(course, march(2), List.of(
                notice("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -8, EnumSet.allOf(ReminderChannel.class), null)));
        publishCourseSchedule(course, march(4), List.of(
                notice("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -2, EnumSet.allOf(ReminderChannel.class), null)));
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                installmentOf(enrollment, march(1), march(11));
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(3), march(9));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(3), march(9));
    }

    @Test
    @Tag("D-368")
    void issuanceNoticeCanBeDisabledByRemovingIt() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        Course course = newCourse();
        CourseEnrollment enrollment = enrollInCourse(student, course);
        publishCourseSchedule(course, LocalDate.of(2027, 2, 27), defaultNoticesWithout("issuance"));
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                installmentOf(enrollment, march(1), march(11));
            }
        });

        harness.run(march(1), march(11));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(6), march(10));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(8), march(10));
    }

    @Test
    @Tag("D-369")
    void exceptionScheduleAppliesOnlyToItsProgram() {
        ReminderTimelineHarness harness = newHarness();
        Student withException = newStudent();
        Student withDefault = newStudent();
        harness.track(withException);
        harness.track(withDefault);
        ProgramEnrollment exceptional = enrollInProgram(withException);
        ProgramEnrollment regular = enrollInProgram(withDefault);
        publishProgramSchedule(exceptional.getProgram(), LocalDate.of(2027, 2, 27), List.of(
                notice("two-days-after-issue", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 2, EnumSet.of(ReminderChannel.IN_APP), null)));
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                installmentOf(exceptional, march(1), march(11));
                installmentOf(regular, march(1), march(11));
            }
        });

        harness.run(march(1), march(11));

        assertThat(harness.inAppDaysOf(withException)).containsExactly(march(3));
        assertThat(harness.emailDaysOf(withException)).isEmpty();
        assertThat(harness.inAppDaysOf(withDefault)).containsExactly(march(1), march(6), march(10));
        assertThat(harness.emailDaysOf(withDefault)).containsExactly(march(8), march(10));
    }

    @Test
    @Tag("D-403")
    @Tag("D-409")
    @Tag("D-413")
    void repeatedNoticeMergesIntoTheDailyDigest() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        Course course = newCourse();
        CourseEnrollment enrollment = enrollInCourse(student, course);
        publishCourseSchedule(course, LocalDate.of(2027, 2, 27), List.of(
                notice("weekly-after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1, EnumSet.allOf(ReminderChannel.class),
                        new ReminderRepetition(7, ReminderMilestone.SUSPENSION))));
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                installmentOf(enrollment, march(1), march(11));
                installmentOf(enrollment, march(1), march(18));
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(12), march(19), march(26));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(12), march(19), march(26));
        assertThat(highlightedLinesOf(student, march(12))).isEqualTo(1);
        assertThat(highlightedLinesOf(student, march(19))).isEqualTo(2);
        assertThat(highlightedLinesOf(student, march(26))).isEqualTo(2);
        assertThat(jdbc.queryForObject("select jsonb_array_length(email_messages) from collection_reminder where student_id = ? and business_date = ?", Integer.class,
                student.getId(), march(19))).isEqualTo(1);
    }

    @Test
    @Tag("D-353")
    @Tag("D-403")
    void installmentAndEnrollmentFeeDueTheSameDayShareOneMessagePerChannelAndDay() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        ProgramEnrollment enrollment = enrollInProgram(student);
        harness.issuing(day -> {
            if (day.equals(march(1))) {
                installmentOf(enrollment, march(1), march(11));
                enrollmentFeeOf(enrollment, march(1), march(11));
            }
        });

        harness.run(march(1), march(31));

        assertThat(harness.inAppDaysOf(student)).containsExactly(march(1), march(6), march(10), march(21));
        assertThat(harness.emailDaysOf(student)).containsExactly(march(8), march(10), march(22));
        assertThat(jdbc.queryForList("select business_date from collection_reminder where student_id = ? and (jsonb_array_length(in_app_messages) > 1 or jsonb_array_length(email_messages) > 1)",
                LocalDate.class, student.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select jsonb_array_length(jsonb_path_query_array(email_messages, '$[*].blocks[*].lines[*]')) from collection_reminder where student_id = ? and business_date = ?",
                Integer.class, student.getId(), march(10))).isEqualTo(2);
    }

    @Test
    @Tag("D-353")
    @Tag("D-403")
    void reminderInvariantsFlagAPaidInvoiceAndTwoDigestsOfTheSameChannel() {
        BillingInvariantChecker checker = BillingInvariantChecker.startingNow(jdbc);
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(march(3))) {
                installmentOf(enrollment, march(3), march(12));
            }
        });
        harness.run(march(3), march(8));
        assertThat(checker.reminderNoticesAreNotPlannedForSettledInvoicesOn(march(8))).isEmpty();
        assertThat(checker.atMostOneDigestPerStudentDayAndChannel()).isEmpty();

        pay(lastInvoiceOf(student), march(8));
        jdbc.update("update collection_reminder set email_messages = replace(email_messages::text, 'OWN_NOTICE', 'DAILY_DIGEST')::jsonb "
                + "|| replace(email_messages::text, 'OWN_NOTICE', 'DAILY_DIGEST')::jsonb where student_id = ? and business_date = ?", student.getId(), march(8));

        assertThat(checker.reminderNoticesAreNotPlannedForSettledInvoicesOn(march(8))).hasSize(3).allMatch(violation -> violation.startsWith("I-R2"));
        assertThat(checker.atMostOneDigestPerStudentDayAndChannel()).hasSize(1).allMatch(violation -> violation.startsWith("I-R1"));
    }

    private int highlightedLinesOf(Student student, LocalDate day) {
        return jdbc.queryForObject("select jsonb_array_length(jsonb_path_query_array(email_messages, '$[*].blocks[*].lines[*] ? (@.notices.size() > 0)')) "
                + "from collection_reminder where student_id = ? and business_date = ?", Integer.class, student.getId(), day);
    }
}
