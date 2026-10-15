package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-148")
@Tag("D-293")
@Tag("D-249")
class EnrollmentWithdrawalLateFeeFreezeIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;

    @Test
    void aWithdrawalInformedForAprilTwentyEighthFreezesTheLateFeeFromThatDayAndKeepsTheAlreadyIssuedAprilDebitNoteWhichOnlyBreaksTheFrozenDaysInvariants() {
        arrange(2107);
        publishDefaults(2107);
        Invoice origin = overdueInstallmentDueOn(d(2107, 4, 11));
        reconcile(origin, d(2107, 5, 1));
        assertThat(summaryOf(origin)).containsExactly("2107-04-12..2107-04-30:19:3800.00");
        long issuedDebitNotes = jdbc.queryForObject("select count(*) from invoice where origin_invoice_id = ?", Long.class, origin.getId());
        MutableClock.INSTANCE.setDate(d(2107, 5, 3));

        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2107, 4, 28), person.getId()));
        reconcile(origin, d(2107, 5, 4));
        reconcile(origin, d(2107, 6, 1));

        assertThat(jdbc.queryForObject("select completion_date::date::text from course_enrollment where id = ?", String.class, enrollment.getId())).isEqualTo("2107-04-28");
        assertThat(summaryOf(origin)).containsExactly("2107-04-12..2107-04-30:19:3800.00");
        assertThat(jdbc.queryForObject("select count(*) from invoice where origin_invoice_id = ?", Long.class, origin.getId())).isEqualTo(issuedDebitNotes);
        assertThatThrownBy(invariants::assertAllBillingInvariantsHold).isInstanceOf(AssertionError.class).satisfies(failure ->
                assertThat(failure.getMessage().lines().skip(1).toList()).isNotEmpty()
                        .allMatch(line -> line.startsWith("I-11c charged days differ") || line.startsWith("I-11e late fee charged for days frozen")));
    }

    @Test
    void aWithdrawalRegisteredWithoutInformedDateFreezesTheLateFeeFromTheRegistrationDay() {
        arrange(2108);
        publishDefaults(2108);
        Invoice origin = overdueInstallmentDueOn(d(2108, 4, 11));
        reconcile(origin, d(2108, 5, 1));
        MutableClock.INSTANCE.setDate(d(2108, 5, 3));

        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(null, person.getId()));
        reconcile(origin, d(2108, 6, 1));

        assertThat(jdbc.queryForObject("select completion_date::date::text from course_enrollment where id = ?", String.class, enrollment.getId())).isEqualTo("2108-05-03");
        assertThat(summaryOf(origin)).containsExactly("2108-04-12..2108-04-30:19:3800.00", "2108-05-01..2108-05-03:3:600.00");
        invariants.assertAllBillingInvariantsHold();
    }
}
