package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.service.AbstractCreditNoteIT;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-142")
@Tag("D-147")
@Tag("D-165")
@Tag("D-296")
@Tag("C-20")
class EnrollmentWithdrawalSequencesIT extends AbstractCreditNoteIT {

    private static final LocalDate START = LocalDate.of(2027, 4, 1);

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    private Long newProgramEnrollment(Long studentId) {
        Long programId = jdbc.queryForObject("insert into program (name, status, total_years, currency) values (?, 'ACTIVE', 1, 'ARS') returning id", Long.class,
                "Sequence " + System.nanoTime());
        return inTransaction(() -> {
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(studentRepository.findById(studentId).orElseThrow());
            enrollment.setProgram(entityManager.getReference(Program.class, programId));
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
            return programEnrollmentRepository.save(enrollment).getId();
        });
    }

    private Map<String, Object> unpaidInvoicesState(Long accountId) {
        return Map.of(
                "invoices", jdbc.queryForList("select id, payment_status, document_status, total, outstanding_amount, due_date from invoice "
                        + "where student_current_account_id = ? and payment_status in ('PENDING', 'OVERDUE', 'PAYMENT_UNDER_REVIEW') order by id", accountId),
                "lines", jdbc.queryForList("select l.id, l.credited_amount, l.charge_key_released_at from invoice_line l join invoice i on i.id = l.invoice_id "
                        + "where i.student_current_account_id = ? and i.payment_status in ('PENDING', 'OVERDUE', 'PAYMENT_UNDER_REVIEW') order by l.id", accountId),
                "creditNotes", jdbc.queryForObject("select count(*) from credit_note where student_current_account_id = ?", Long.class, accountId),
                "entries", jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, accountId));
    }

    private Invoice issueInstallment(Long enrollmentId, YearMonth period, boolean overdue) {
        ProgramEnrollment enrollment = inTransaction(() -> programEnrollmentRepository.findById(enrollmentId).orElseThrow());
        var fixture = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).forPeriod(period).dueOn(period.atDay(10))
                .withBaseAmount(Money.of(new BigDecimal("80000.00"), Currency.ARS));
        return (overdue ? fixture.overdue() : fixture.pending()).build();
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void randomSequencesOfIssuingPayingAndWithdrawingNeverWriteOnUnpaidInvoices(long seed) {
        Random random = new Random(seed);
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        List<Long> activeEnrollments = new ArrayList<>(List.of(newProgramEnrollment(studentId), newProgramEnrollment(studentId)));
        int[] nextMonthOffset = {0, 0};
        MutableClock.INSTANCE.setDate(START);
        Long staffPersonId = newStaff("ADMINISTRATIVO").personId();
        int withdrawals = 0;

        for (int step = 0; step < 8; step++) {
            int action = random.nextInt(5);
            int target = random.nextInt(2);
            Long enrollmentId = activeEnrollments.get(target);
            boolean isActive = !jdbc.queryForObject("select status from program_enrollment where id = ?", String.class, enrollmentId).equals("WITHDRAWN");
            switch (action) {
                case 0, 1 -> {
                    if (isActive) {
                        issueInstallment(enrollmentId, YearMonth.of(2027, 3 + nextMonthOffset[target]++), action == 1);
                    }
                }
                case 2 -> {
                    List<BigDecimal> outstanding = jdbc.queryForList("select outstanding_amount from invoice where student_current_account_id = ? "
                            + "and payment_status in ('PENDING', 'OVERDUE') order by due_date, id limit 1", BigDecimal.class, accountId);
                    if (!outstanding.isEmpty()) {
                        payAccount(accountId, outstanding.get(0).toPlainString(), MutableClock.INSTANCE.instant().atZone(MutableClock.INSTANCE.getZone()).toLocalDate());
                    }
                }
                case 3 -> MutableClock.INSTANCE.setDate(MutableClock.INSTANCE.instant().atZone(MutableClock.INSTANCE.getZone()).toLocalDate().plusDays(1 + random.nextInt(9)));
                default -> {
                    if (isActive) {
                        withdrawAndAssertUnpaidInvoicesUntouched(enrollmentId, accountId, staffPersonId, random.nextBoolean(), seed);
                        withdrawals++;
                    }
                }
            }
            invariants.assertAllBillingInvariantsHold();
        }
        if (withdrawals == 0) {
            withdrawAndAssertUnpaidInvoicesUntouched(activeEnrollments.get(0), accountId, staffPersonId, seed % 2 == 0, seed);
            withdrawals++;
            invariants.assertAllBillingInvariantsHold();
        }
        assertThat(withdrawals).isGreaterThanOrEqualTo(1);
    }

    private void withdrawAndAssertUnpaidInvoicesUntouched(Long enrollmentId, Long accountId, Long staffPersonId, boolean voluntary, long seed) {
        Map<String, Object> before = unpaidInvoicesState(accountId);
        EnrollmentWithdrawal withdrawal = voluntary ? EnrollmentWithdrawal.voluntaryRegisteredBy(null, staffPersonId) : EnrollmentWithdrawal.forNonpayment();
        programEnrollmentService.withdraw(enrollmentId, withdrawal);
        assertThat(unpaidInvoicesState(accountId)).as("seed %d enrollment %d", seed, enrollmentId).isEqualTo(before);
    }
}
