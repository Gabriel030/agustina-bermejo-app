package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Runs day-by-day timelines of the reminder jobs, seven scenarios in one class")
class DebtSummaryTimelineIT extends AbstractReminderTimelineIT {
    private static final String WAITS_FOR_PRIOR_DEBT = "NEW_INSTALLMENT_WAITS_FOR_PRIOR_DEBT";

    @Autowired
    private ConfigurationVersionRepository configurationVersionRepository;

    private static LocalDate april(int day) {
        return LocalDate.of(2027, 4, day);
    }

    private String emailFormatOn(Student student, LocalDate day) {
        return jdbc.queryForObject("select email_messages->0->>'format' from collection_reminder where student_id = ? and business_date = ?", String.class, student.getId(), day);
    }

    private long suppressedRowsWith(Student student, LocalDate day, String code) {
        return jdbc.queryForObject("select count(*) from collection_reminder where student_id = ? and business_date = ? and suppressed::text like ?", Long.class,
                student.getId(), day, "%" + code + "%");
    }

    private String emailBodyOn(Student student, LocalDate day) {
        return jdbc.queryForObject("select body_text from notification_delivery where recipient_person_id = ? and channel = 'EMAIL' and scheduled_for = ?", String.class,
                student.getPerson().getId(), day);
    }

    private String emailSubjectOn(Student student, LocalDate day) {
        return jdbc.queryForObject("select subject from notification_delivery where recipient_person_id = ? and channel = 'EMAIL' and scheduled_for = ?", String.class,
                student.getPerson().getId(), day);
    }

    @Test
    @Tag("D-347")
    @Tag("D-344")
    @Tag("D-353")
    void newInstallmentWithAnUnpaidPriorOneGetsOneDebtSummaryAndNoOwnNotices() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        Invoice march = installmentOf(enrollment, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 11));
        harness.issuing(day -> {
            if (day.equals(april(1))) {
                installmentOf(enrollment, april(1), april(11));
            }
        });

        harness.run(april(1), april(12));

        assertThat(harness.emailDaysOf(student)).containsExactly(april(1));
        assertThat(harness.inAppDaysOf(student)).containsExactly(april(1));
        assertThat(harness.sentEmailDaysOf(student)).containsExactly(april(1));
        assertThat(emailFormatOn(student, april(1))).isEqualTo("DEBT_SUMMARY");
        assertThat(jdbc.queryForObject("select (email_messages->0->'blocks'->0->'lines'->0->>'invoiceId')::bigint from collection_reminder where student_id = ? and business_date = ?",
                Long.class, student.getId(), april(1))).isEqualTo(march.getId());
        assertThat(suppressedRowsWith(student, april(6), WAITS_FOR_PRIOR_DEBT)).isEqualTo(1L);
        assertThat(suppressedRowsWith(student, april(9), WAITS_FOR_PRIOR_DEBT)).isEqualTo(1L);
        assertThat(suppressedRowsWith(student, april(10), WAITS_FOR_PRIOR_DEBT)).isEqualTo(1L);
        assertThat(emailSubjectOn(student, april(1))).isEqualTo("Resumen de tu deuda");
        assertThat(emailBodyOn(student, april(1))).contains("Los pagos se aplican primero a lo más vencido.", "Además, se emitió una cuota nueva");
    }

    @Test
    @Tag("D-347")
    void withThePriorInstallmentPaidOnTheThirdTheNewOneHasItsMailsOnTheSixthAndTheNinth() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        Invoice march = installmentOf(enrollment, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 11));
        harness.issuing(day -> {
            if (day.equals(april(1))) {
                installmentOf(enrollment, april(1), april(11));
            }
        }).during(day -> {
            if (day.equals(april(3))) {
                pay(march, day);
            }
        });

        harness.run(april(1), april(12));

        assertThat(harness.emailDaysOf(student)).containsExactly(april(1), april(6), april(9));
        assertThat(harness.inAppDaysOf(student)).containsExactly(april(1), april(6), april(10));
        assertThat(emailFormatOn(student, april(6))).isNotEqualTo("DEBT_SUMMARY");
    }

    @Test
    @Tag("D-347")
    @Tag("D-391")
    @Tag("D-403")
    void theSummaryComesOutEvenAfterFourMailsOfTheMonth() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        Course course = newCourse();
        CourseEnrollment enrollment = enrollInCourse(student, course);
        publishCourseSchedule(course, LocalDate.of(2027, 2, 27), List.of(
                notice("issuance", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0, EnumSet.of(ReminderChannel.IN_APP), null),
                notice("after-issue", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 2, EnumSet.allOf(ReminderChannel.class), null),
                notice("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1, EnumSet.allOf(ReminderChannel.class), null),
                notice("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1, EnumSet.allOf(ReminderChannel.class), null),
                notice("after-due-late", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 2, EnumSet.allOf(ReminderChannel.class), null)));
        installmentOf(enrollment, april(1), april(8));
        harness.issuing(day -> {
            if (day.equals(LocalDate.of(2027, 5, 3))) {
                installmentOf(enrollment, LocalDate.of(2027, 5, 3), LocalDate.of(2027, 5, 13));
            }
        });

        harness.run(april(3), april(12));
        harness.runDay(LocalDate.of(2027, 5, 3));

        assertThat(harness.emailDaysOf(student)).containsExactly(april(5), april(7), april(9), april(12), LocalDate.of(2027, 5, 3));
        assertThat(emailFormatOn(student, LocalDate.of(2027, 5, 3))).isEqualTo("DEBT_SUMMARY");
        assertThat(harness.sentEmailDaysOf(student)).contains(LocalDate.of(2027, 5, 3));
    }

    @Test
    @Tag("D-344")
    void studentUpToDateGetsTheIssuanceInAppWithoutMail() {
        ReminderTimelineHarness harness = newHarness();
        Student student = newStudent();
        harness.track(student);
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        harness.issuing(day -> {
            if (day.equals(april(1))) {
                installmentOf(enrollment, april(1), april(11));
            }
        });

        harness.run(april(1), april(1));

        assertThat(harness.inAppDaysOf(student)).containsExactly(april(1));
        assertThat(harness.emailDaysOf(student)).isEmpty();
    }

    private Student studentWithInstallmentDue(LocalDate issueDate, LocalDate dueDate) {
        Student student = newStudent();
        CourseEnrollment enrollment = enrollInCourse(student, newCourse());
        installmentOf(enrollment, issueDate, dueDate);
        return student;
    }

    @Test
    @Tag("D-345")
    @Tag("D-330")
    @Tag("D-331")
    @Tag("D-288")
    void beforeDueMailStatesTheDailyRateAndTheGraceOfTheVersionInForce() {
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, newPerson("direccion", "DIRECTOR", true), 5, new BigDecimal("0.0017"), 30, 2, 10, true, true,
                LocalDate.of(2056, 2, 27));
        Student student = studentWithInstallmentDue(LocalDate.of(2056, 3, 1), LocalDate.of(2056, 3, 11));
        ReminderTimelineHarness harness = newHarness();
        harness.track(student);

        harness.run(LocalDate.of(2056, 3, 10), LocalDate.of(2056, 3, 10));

        assertThat(emailBodyOn(student, LocalDate.of(2056, 3, 10))).contains("si se paga después del 16/03/2056, corre un recargo de 0,17 % por día desde el 11/03/2056 (ARS 170,00 por día)");
        assertThat(emailSubjectOn(student, LocalDate.of(2056, 3, 10))).doesNotContainPattern("\\d").doesNotContain("ARS");
    }

    @Test
    @Tag("D-345")
    @Tag("D-288")
    void theRateIsTheOneOfTheVersionInForceOnTheDueDateAndNotOnTheSendingDay() {
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, newPerson("direccion", "DIRECTOR", true), 5, new BigDecimal("0.0017"), 30, 2, 10, true, true,
                LocalDate.of(2058, 2, 27));
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, newPerson("direccion", "DIRECTOR", true), 2, new BigDecimal("0.0100"), 30, 2, 10, true, true,
                LocalDate.of(2058, 3, 12));
        Student student = studentWithInstallmentDue(LocalDate.of(2058, 3, 1), LocalDate.of(2058, 3, 12));
        ReminderTimelineHarness harness = newHarness();
        harness.track(student);

        harness.run(LocalDate.of(2058, 3, 11), LocalDate.of(2058, 3, 11));

        assertThat(emailBodyOn(student, LocalDate.of(2058, 3, 11))).contains("si se paga después del 14/03/2058, corre un recargo de 1 % por día desde el 12/03/2058 (ARS 1.000,00 por día)");
    }

    @Test
    @Tag("D-345")
    @Tag("D-306")
    void afterDueMailDescribesTheSuspensionRuleInForce() {
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, newPerson("direccion", "DIRECTOR", true), 5, new BigDecimal("0.0017"), 30, 2, 10, true, false,
                LocalDate.of(2057, 2, 27));
        Student student = studentWithInstallmentDue(LocalDate.of(2057, 3, 1), LocalDate.of(2057, 3, 11));
        ReminderTimelineHarness harness = newHarness();
        harness.track(student);

        harness.run(LocalDate.of(2057, 3, 21), LocalDate.of(2057, 3, 21));

        assertThat(emailBodyOn(student, LocalDate.of(2057, 3, 21))).contains("la inscripción puede suspenderse si hay 2 facturas vencidas o más, o si pasan más de 30 días desde el vencimiento")
                .doesNotContain("corre un recargo");
    }
}
