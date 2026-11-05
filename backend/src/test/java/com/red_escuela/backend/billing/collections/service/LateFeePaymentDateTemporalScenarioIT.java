package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Walks every day from March to May running the real nightly late fee run, payment events and every billing invariant against Postgres")
class LateFeePaymentDateTemporalScenarioIT extends AbstractLateFeeScenarioIT {
    private static final int YEAR = 2560;
    private static final int GRACE_DAYS = 5;

    @Autowired
    private LateFeeAccrualStep accrualStep;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private record Installment(String label, Invoice origin, LocalDate due, LocalDate paymentDate, LocalDate registeredOn, Optional<LocalDate> enrollmentEnd, int expectedNetDays) {
    }

    private static int oracleNetDays(LocalDate due, LocalDate paymentDate, Optional<LocalDate> enrollmentEnd) {
        if (!paymentDate.isAfter(due.plusDays(GRACE_DAYS))) {
            return 0;
        }
        LocalDate lastChargeableDay = enrollmentEnd.filter(end -> end.isBefore(paymentDate)).orElse(paymentDate);
        int days = 0;
        for (LocalDate day = due.plusDays(1); !day.isAfter(lastChargeableDay); day = day.plusDays(1)) {
            days++;
        }
        return days;
    }

    private Installment installment(String label, Invoice origin, LocalDate due, LocalDate paymentDate, LocalDate registeredOn, Optional<LocalDate> enrollmentEnd) {
        return new Installment(label, origin, due, paymentDate, registeredOn, enrollmentEnd, oracleNetDays(due, paymentDate, enrollmentEnd));
    }

    private void registerPayment(Installment installment) {
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(installment.origin(), installment.paymentDate(), installment.registeredOn());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(InvoicePaidDomainEvent.paidFor(installment.origin().getId(),
                installment.origin().getStudentCurrentAccountId(), InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name(), installment.registeredOn())));
    }

    private int netChargedDaysOf(Invoice origin) {
        return jdbc.queryForObject("select coalesce(sum(c.charged_days), 0) - coalesce((select sum(k.corrected_days) from late_fee_charge_correction k "
                + "join late_fee_charge c2 on c2.id = k.late_fee_charge_id where c2.origin_invoice_id = ?), 0) from late_fee_charge c where c.origin_invoice_id = ?",
                Integer.class, origin.getId(), origin.getId());
    }

    @Test
    @Tag("D-330")
    @Tag("D-331")
    @Tag("D-378")
    @Tag("D-293")
    void temporalScenarioOfBackdatedPaymentsKeepsNetDaysEqualToTheOracle() {
        arrange(YEAR);
        publish(GRACE_DAYS, "0.002", 10, true, true, d(YEAR, 1, 1));
        Invoice fiveDaysLate = overdueInstallmentDueOn(d(YEAR, 3, 11));
        Invoice thirtyDaysLate = overdueInstallmentDueOn(d(YEAR, 3, 18));
        Invoice insideTheGrace = overdueInstallmentDueOn(d(YEAR, 3, 11));
        Invoice oneDayLate = overdueInstallmentDueOn(d(YEAR, 4, 11));
        Invoice withPaidDebitNote = overdueInstallmentDueOn(d(YEAR, 3, 21));
        List<Installment> installments = new ArrayList<>(List.of(
                installment("five days late", fiveDaysLate, d(YEAR, 3, 11), d(YEAR, 3, 28), d(YEAR, 4, 2), Optional.empty()),
                installment("thirty days late", thirtyDaysLate, d(YEAR, 3, 18), d(YEAR, 3, 25), d(YEAR, 4, 24), Optional.empty()),
                installment("inside the grace", insideTheGrace, d(YEAR, 3, 11), d(YEAR, 3, 16), d(YEAR, 4, 2), Optional.empty()),
                installment("one day late", oneDayLate, d(YEAR, 4, 11), d(YEAR, 4, 29), d(YEAR, 5, 2), Optional.empty()),
                installment("debit note already paid", withPaidDebitNote, d(YEAR, 3, 21), d(YEAR, 3, 28), d(YEAR, 4, 10), Optional.empty())));
        arrange(YEAR);
        withdrawCourseEnrollmentOn(d(YEAR, 4, 20));
        Invoice frozenByWithdrawal = overdueInstallmentDueOn(d(YEAR, 4, 11));
        installments.add(installment("frozen by the withdrawal", frozenByWithdrawal, d(YEAR, 4, 11), d(YEAR, 4, 18), d(YEAR, 5, 3), Optional.of(d(YEAR, 4, 20))));

        for (LocalDate day = d(YEAR, 3, 12); !day.isAfter(d(YEAR, 5, 10)); day = day.plusDays(1)) {
            MutableClock.INSTANCE.setDate(day);
            long rowsBefore = billingRowCount();
            boolean activity = false;
            for (Installment installment : installments) {
                if (installment.registeredOn().equals(day)) {
                    registerPayment(installment);
                    activity = true;
                }
            }
            if (day.equals(d(YEAR, 4, 5))) {
                payIssuedDebitNote(withPaidDebitNote, day);
                activity = true;
            }
            if (activity) {
                awaitEventsDrained();
            }
            accrualStep.accrueLateFees(day);
            boolean closeDay = day.getDayOfMonth() == 1;
            if (activity || closeDay) {
                awaitEventsDrained();
                assertScenarioInvariants(day);
            } else {
                assertThat(billingRowCount()).as("the nightly run of a quiet day %s changed billing rows", day).isEqualTo(rowsBefore);
            }
        }
        awaitEventsDrained();

        for (Installment installment : installments) {
            assertThat(netChargedDaysOf(installment.origin())).as("net days of the installment %s", installment.label()).isEqualTo(installment.expectedNetDays());
        }
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, chargesOf(insideTheGrace).get(0).get("debit_note_id"))).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from credit_note n join invoice d on d.id = n.invoice_id join late_fee_charge c on c.debit_note_id = d.id "
                + "where c.origin_invoice_id = ? and n.reason = 'LATE_FEE_ANNULMENT'", Long.class, withPaidDebitNote.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select d.payment_status from invoice d join late_fee_charge c on c.debit_note_id = d.id where c.origin_invoice_id = ?", String.class,
                withPaidDebitNote.getId())).isEqualTo("PAID");
        assertScenarioInvariants(d(YEAR, 5, 10));
    }

    private long billingRowCount() {
        return jdbc.queryForObject("select (select count(*) from late_fee_charge) + (select count(*) from credit_note) + (select count(*) from payment_allocation) "
                + "+ (select count(*) from invoice) + (select count(*) from late_fee_charge_correction)", Long.class);
    }

    private void payIssuedDebitNote(Invoice origin, LocalDate day) {
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(invoiceRepository.findInvoiceWithLinesById(debitNoteId).orElseThrow(), day, day);
    }
}
