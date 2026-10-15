package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-375")
@Tag("D-293")
@Tag("D-249")
@SlowTest(reason = "Runs random sequences of nightly runs, payments, receipts under review and withdrawals checking the billing invariants after every step")
class LateFeeCorrectionSequencesIT extends AbstractLateFeeCorrectionIT {

    private static final int SEQUENCES = 3;

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private LateFeeChargeRepository chargeRepository;

    private List<Long> debitNotesIn(InvoicePaymentStatus... statuses) {
        List<String> names = java.util.Arrays.stream(statuses).map(Enum::name).toList();
        return jdbc.queryForList("select c.debit_note_id from late_fee_charge c join invoice d on d.id = c.debit_note_id "
                + "join invoice o on o.id = c.origin_invoice_id where o.student_current_account_id = ? and d.payment_status in ("
                + String.join(",", names.stream().map(name -> "'" + name + "'").toList()) + ") order by c.debit_note_id", Long.class, accountId());
    }

    private Long accountId() {
        return jdbc.queryForObject("select id from student_current_account where student_id = ?", Long.class, student.getId());
    }

    private void markUnderReview(Long debitNoteId, LocalDate date) {
        try {
            transactionTemplate.executeWithoutResult(status -> invoiceRepository.findByIdForUpdate(debitNoteId).orElseThrow()
                    .markPaymentUnderReview(date, ACTOR, "Comprobante"));
        } catch (RuntimeException ignoredBecauseTheStateDoesNotAllowReview) {
        }
    }

    private void rejectReview(Long debitNoteId, LocalDate date) {
        transactionTemplate.executeWithoutResult(status -> {
            Invoice reviewed = invoiceRepository.findByIdForUpdate(debitNoteId).orElseThrow();
            reviewed.releasePaymentReview(date, 0, ACTOR, "Comprobante rechazado");
            reviewed.pullRecordedDomainEvents().forEach(eventPublisher::publishEvent);
        });
    }

    @Test
    void anySequenceOfNightlyRunsPaymentsReceiptsAndRetroactiveWithdrawalsKeepsTheBillingInvariantsAndLeavesNoDayAfterTheEndChargedNet() {
        Random random = new Random(20261015L);
        for (int sequence = 0; sequence < SEQUENCES; sequence++) {
            int year = 2200 + sequence;
            arrange(year);
            publishDefaults(year);
            List<Invoice> origins = List.of(overdueInstallmentDueOn(d(year, 3, 11)), overdueInstallmentDueOn(d(year, 4, 11)));
            LocalDate date = d(year, 5, 2);
            boolean withdrawn = false;
            int steps = 4 + random.nextInt(5);
            for (int step = 0; step < steps; step++) {
                date = date.plusDays(random.nextInt(9));
                MutableClock.INSTANCE.setDate(date);
                LocalDate today = date;
                switch (random.nextInt(6)) {
                    case 0 -> origins.forEach(origin -> reconcile(origin, today));
                    case 1 -> {
                        Invoice toPay = origins.get(random.nextInt(origins.size()));
                        if (reload(toPay).getPaymentStatus() != InvoicePaymentStatus.PAID) {
                            payInFull(toPay, today, today);
                        }
                    }
                    case 2 -> {
                        List<Long> open = debitNotesIn(InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE);
                        if (!open.isEmpty()) {
                            markUnderReview(open.get(random.nextInt(open.size())), today);
                        }
                    }
                    case 3 -> debitNotesIn(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW).stream().findFirst().ifPresent(reviewed -> rejectReview(reviewed, today));
                    case 4 -> {
                        if (!withdrawn) {
                            LocalDate earliest = today.withDayOfMonth(1).minusMonths(1);
                            LocalDate informed = today.minusDays(random.nextInt((int) ChronoUnit.DAYS.between(earliest, today) + 1));
                            courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(informed, person.getId()));
                            withdrawn = true;
                        }
                    }
                    default -> correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(today);
                }
                awaitEventsDrained();
                if (debitNotesIn(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW).isEmpty()) {
                    invariants.assertAllBillingInvariantsHold();
                }
            }
            for (Long reviewed : debitNotesIn(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW)) {
                rejectReview(reviewed, date);
            }
            awaitEventsDrained();
            correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(date);
            awaitEventsDrained();

            invariants.assertAllBillingInvariantsHold();
            List<Long> ownCharges = new ArrayList<>();
            origins.forEach(origin -> chargesOf(origin).forEach(charge -> ownCharges.add((Long) charge.get("id"))));
            assertThat(chargeRepository.findChargeIdsWithDaysAfterTheEnrollmentEndAfter(0L, 100_000)).as("sequence " + sequence).doesNotContainAnyElementsOf(ownCharges);
            MutableClock.INSTANCE.reset();
        }
    }
}
