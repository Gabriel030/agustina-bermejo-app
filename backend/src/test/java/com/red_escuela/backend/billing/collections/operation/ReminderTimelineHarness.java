package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.service.OverdueInvoiceTransitionService;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.service.EnrollmentBillingComplianceRowProcessor;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.notification.delivery.service.EmailDeliveryDispatchJob;
import com.red_escuela.backend.shared.time.MutableClock;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

final class ReminderTimelineHarness {
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(30);

    private final JdbcTemplate jdbc;
    private final CollectionReminderPlanningStep planningStep;
    private final OverdueInvoiceTransitionService overdueTransition;
    private final EnrollmentBillingComplianceRowProcessor complianceProcessor;
    private final BillingConfigurationReader configurationReader;
    private final EmailDeliveryDispatchJob dispatchJob;
    private final BillingInvariantChecker invariants;
    private final List<Long> studentIds = new ArrayList<>();
    private final List<Long> personIds = new ArrayList<>();
    private Consumer<LocalDate> issuance = day -> {
    };
    private Consumer<LocalDate> duringTheDay = day -> {
    };

    ReminderTimelineHarness(JdbcTemplate jdbc, CollectionReminderPlanningStep planningStep, OverdueInvoiceTransitionService overdueTransition,
                            EnrollmentBillingComplianceRowProcessor complianceProcessor, BillingConfigurationReader configurationReader, EmailDeliveryDispatchJob dispatchJob) {
        this.jdbc = jdbc;
        this.planningStep = planningStep;
        this.overdueTransition = overdueTransition;
        this.complianceProcessor = complianceProcessor;
        this.configurationReader = configurationReader;
        this.dispatchJob = dispatchJob;
        this.invariants = BillingInvariantChecker.startingNow(jdbc);
    }

    ReminderTimelineHarness issuing(Consumer<LocalDate> issuanceAtFourAm) {
        this.issuance = issuanceAtFourAm;
        return this;
    }

    ReminderTimelineHarness during(Consumer<LocalDate> dayEvents) {
        this.duringTheDay = dayEvents;
        return this;
    }

    void track(Student student) {
        studentIds.add(student.getId());
        personIds.add(student.getPerson().getId());
    }

    void run(LocalDate from, LocalDate toInclusive) {
        for (LocalDate day = from; !day.isAfter(toInclusive); day = day.plusDays(1)) {
            runDay(day);
        }
    }

    void runDay(LocalDate day) {
        runDayUntilPlanned(day);
        for (int hour = 9; hour <= 11; hour++) {
            at(day, hour, 0);
            dispatchJob.dispatchDueEmails();
        }
        at(day, 12, 0);
        duringTheDay.accept(day);
        invariants.assertAllBillingInvariantsHold();
    }

    void runDayUntilPlanned(LocalDate day) {
        at(day, 3, 0);
        runOverdueSweep(day);
        at(day, 3, 1);
        runComplianceSweep(day);
        if (day.getDayOfMonth() <= 5) {
            at(day, 4, 0);
            issuance.accept(day);
        }
        at(day, 5, 0);
        planAndAwaitDelivery(day);
        invariants.assertReminderInvariantsHoldOn(day);
    }

    void dispatchAt(LocalDate day, int hour, int minute) {
        at(day, hour, minute);
        dispatchJob.dispatchDueEmails();
        invariants.assertAllBillingInvariantsHold();
    }

    void atTime(LocalDate day, int hour, int minute) {
        at(day, hour, minute);
    }

    private void at(LocalDate day, int hour, int minute) {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(day, LocalTime.of(hour, minute)));
    }

    private void runOverdueSweep(LocalDate day) {
        if (studentIds.isEmpty()) {
            return;
        }
        List<Long> pending = jdbc.queryForList("select i.id from invoice i join student_current_account a on a.id = i.student_current_account_id "
                + "where a.student_id in (" + idList(studentIds) + ") and i.payment_status = 'PENDING' and i.due_date < ? order by i.id", Long.class, day);
        pending.forEach(invoiceId -> overdueTransition.markAsOverdueWhenPastDueDate(invoiceId, day));
    }

    private void runComplianceSweep(LocalDate day) {
        if (studentIds.isEmpty()) {
            return;
        }
        Optional<LatePaymentTerms> policy = configurationReader.findLatePaymentSettingsEffectiveOn(day);
        if (policy.isEmpty()) {
            return;
        }
        jdbc.queryForList("select id from course_enrollment where status = 'ENROLLED' and student_id in (" + idList(studentIds) + ") order by id", Long.class)
                .forEach(id -> complianceProcessor.processCourseEnrollment(id, policy.get(), day));
        jdbc.queryForList("select id from program_enrollment where status = 'ENROLLED' and student_id in (" + idList(studentIds) + ") order by id", Long.class)
                .forEach(id -> complianceProcessor.processProgramEnrollment(id, policy.get(), day));
    }

    private void planAndAwaitDelivery(LocalDate day) {
        if (studentIds.isEmpty()) {
            return;
        }
        CollectionReminderPlanningResult result = planningStep.planCollectionRemindersOf(day, studentIds);
        assertThat(result.errors()).as("planning errors on " + day).isEmpty();
        Long expected = jdbc.queryForObject("select coalesce(sum(jsonb_array_length(in_app_messages) + jsonb_array_length(email_messages)), 0) from collection_reminder "
                + "where business_date = ? and student_id in (" + idList(studentIds) + ")", Long.class, day);
        await().atMost(DELIVERY_TIMEOUT).untilAsserted(() -> assertThat(jdbc.queryForObject("select count(*) from notification_delivery "
                + "where scheduled_for = ? and recipient_person_id in (" + idList(personIds) + ")", Long.class, day)).as("deliveries of " + day).isEqualTo(expected));
    }

    List<LocalDate> inAppDaysOf(Student student) {
        return daysOf(student, "in_app_messages");
    }

    List<LocalDate> emailDaysOf(Student student) {
        return daysOf(student, "email_messages");
    }

    List<LocalDate> sentEmailDaysOf(Student student) {
        return jdbc.queryForList("select scheduled_for from notification_delivery where recipient_person_id = ? and channel = 'EMAIL' "
                + "and status in ('SENT', 'SENT_SIMULATED') order by scheduled_for", LocalDate.class, student.getPerson().getId());
    }

    private List<LocalDate> daysOf(Student student, String column) {
        return jdbc.queryForList("select business_date from collection_reminder where student_id = ? and jsonb_array_length(" + column + ") > 0 order by business_date",
                LocalDate.class, student.getId());
    }

    private static String idList(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
