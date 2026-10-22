package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.service.AbstractCreditNoteIT;
import com.red_escuela.backend.billing.note.service.LeftOutInvoiceCancellationOnReceiptRejectionListener;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("D-142")
@Tag("D-147")
@Tag("D-165")
@Tag("D-296")
@Tag("C-20")
@SlowTest(reason = "Twelve random multi-step withdrawal sequences with real director approvals and a rejected-receipt cancellation, each on its own account")
class EnrollmentWithdrawalSequencesIT extends AbstractCreditNoteIT {

    private static final LocalDate START = LocalDate.of(2027, 4, 1);
    private static final AtomicInteger APPROVED_REQUESTS = new AtomicInteger();
    private static final AtomicInteger NEXT_YEAR_FEES_CANCELLED_ON_REJECTION = new AtomicInteger();

    @AfterAll
    static void theSequencesExercisedTheDirectorApprovalAndTheRejectedReceiptCancellation() {
        assertThat(APPROVED_REQUESTS.get()).as("requests approved by the director across the sequences").isPositive();
        assertThat(NEXT_YEAR_FEES_CANCELLED_ON_REJECTION.get()).as("next year fees cancelled after a rejected receipt").isPositive();
    }

    @Autowired
    private LeftOutInvoiceCancellationOnReceiptRejectionListener rejectionListener;
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
        Long nextYearFee = seed % 3 == 0 ? nextYearFeeUnderReview(activeEnrollments.get(0)) : null;

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
        if (nextYearFee != null) {
            rejectTheReceiptAndAssertTheNextYearFee(activeEnrollments.get(0), nextYearFee, accountId, staffPersonId, seed);
            invariants.assertAllBillingInvariantsHold();
        }
    }

    private Long nextYearFeeUnderReview(Long enrollmentId) {
        ProgramEnrollment enrollment = inTransaction(() -> {
            ProgramEnrollment loaded = programEnrollmentRepository.findById(enrollmentId).orElseThrow();
            loaded.getProgram().getName();
            loaded.getStudent().getPerson().getName();
            return loaded;
        });
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        inTransaction(() -> {
            invoiceRepository.findById(fee).orElseThrow().markPaymentUnderReview(START, ACTOR, "Comprobante subido");
            return null;
        });
        return fee;
    }

    private void rejectTheReceiptAndAssertTheNextYearFee(Long enrollmentId, Long fee, Long accountId, Long staffPersonId, long seed) {
        boolean stillActive = !jdbc.queryForObject("select status from program_enrollment where id = ?", String.class, enrollmentId).equals("WITHDRAWN");
        if (stillActive) {
            withdrawAndAssertUnpaidInvoicesUntouched(enrollmentId, accountId, staffPersonId, true, seed);
        }
        boolean voluntary = jdbc.queryForObject("select withdrawn_by_person_id is not null from program_enrollment where id = ?", Boolean.class, enrollmentId);
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, fee)).isEqualTo("PAYMENT_UNDER_REVIEW");
        inTransaction(() -> {
            invoiceRepository.findById(fee).orElseThrow().releasePaymentReviewWithoutLateTerms(MutableClock.INSTANCE.instant().atZone(MutableClock.INSTANCE.getZone()).toLocalDate(),
                    ACTOR, "Comprobante rechazado");
            return null;
        });
        inTransaction(() -> rejectionListener.cancelIfLeftOutOfWithdrawal(fee));

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, fee)).as("seed %d next year fee", seed)
                .isEqualTo(voluntary ? "CANCELLED" : "PENDING");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, fee)).isEqualTo(voluntary ? 1L : 0L);
        if (voluntary) {
            NEXT_YEAR_FEES_CANCELLED_ON_REJECTION.incrementAndGet();
        }
    }

    private void withdrawAndAssertUnpaidInvoicesUntouched(Long enrollmentId, Long accountId, Long staffPersonId, boolean voluntary, long seed) {
        Map<String, Object> before = unpaidInvoicesState(accountId);
        EnrollmentWithdrawal withdrawal = voluntary ? EnrollmentWithdrawal.voluntaryRegisteredBy(null, staffPersonId) : EnrollmentWithdrawal.forNonpayment();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(staffPersonId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATIVO"))));
        try {
            programEnrollmentService.withdraw(enrollmentId, withdrawal);
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(unpaidInvoicesState(accountId)).as("seed %d enrollment %d", seed, enrollmentId).isEqualTo(before);
        approvePendingRequestsAndAssertOnlyTheirInvoicesChange(accountId, before, seed);
    }

    private void approvePendingRequestsAndAssertOnlyTheirInvoicesChange(Long accountId, Map<String, Object> before, long seed) {
        List<Long> requestIds = jdbc.queryForList("select distinct ari.authorization_request_id from authorization_request_invoice ari "
                + "join authorization_request ar on ar.id = ari.authorization_request_id join invoice i on i.id = ari.invoice_id "
                + "where ar.status = 'PENDING' and i.student_current_account_id = ? order by 1", Long.class, accountId);
        if (requestIds.isEmpty()) {
            return;
        }
        List<Long> requestedInvoiceIds = jdbc.queryForList("select distinct ari.invoice_id from authorization_request_invoice ari "
                + "join authorization_request ar on ar.id = ari.authorization_request_id join invoice i on i.id = ari.invoice_id "
                + "where ar.status = 'PENDING' and i.student_current_account_id = ?", Long.class, accountId);
        Staff director = newStaff("DIRECTOR");
        requestIds.forEach(requestId -> approveAsDirector(requestId, director));
        APPROVED_REQUESTS.addAndGet(requestIds.size());

        assertThat(requestedInvoiceIds).isNotEmpty();
        requestedInvoiceIds.forEach(invoiceId -> assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId))
                .as("seed %d invoice %d of the approved request", seed, invoiceId).isEqualTo("CANCELLED"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoicesBefore = (List<Map<String, Object>>) before.get("invoices");
        List<Map<String, Object>> untouchedBefore = invoicesBefore.stream().filter(row -> !requestedInvoiceIds.contains(((Number) row.get("id")).longValue())).toList();
        List<Long> untouchedIds = untouchedBefore.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoicesAfter = (List<Map<String, Object>>) unpaidInvoicesState(accountId).get("invoices");
        assertThat(invoicesAfter.stream().filter(row -> untouchedIds.contains(((Number) row.get("id")).longValue())).toList())
                .as("seed %d invoices outside the approved requests", seed).isEqualTo(untouchedBefore);
        assertThat(jdbc.queryForObject("select count(*) from credit_note where student_current_account_id = ?", Long.class, accountId))
                .as("seed %d one credit note per cancelled invoice", seed).isEqualTo((long) requestedInvoiceIds.size());
    }

    private void approveAsDirector(Long requestId, Staff director) {
        try {
            mockMvc.perform(post("/authorization-requests/" + requestId + "/approve").header("Authorization", "Bearer " + director.token())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"decisionReason\":\"ok\"}")).andExpect(status().isOk());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
