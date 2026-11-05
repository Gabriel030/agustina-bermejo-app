package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.billing.payment.service.CashPaymentPreviewService;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag("D-144")
@Tag("D-153")
@Tag("D-094")
@Tag("D-206")
@Tag("D-262")
@Tag("D-159")
class PaymentOperationProperties extends AbstractRefundIT {
    private static final long SEED = 20261028L;
    private static final int SCENARIOS = 6;
    private static final int RANDOM_STEPS = 12;

    @Autowired
    ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired
    RejectPaymentReceiptUseCase rejectReceipt;
    @Autowired
    RegisterCashPaymentOperation cashOperation;
    @Autowired
    CashPaymentPreviewService cashPreviewService;

    private final AtomicInteger cashPayments = new AtomicInteger();
    private final AtomicInteger approvedReceipts = new AtomicInteger();
    private final AtomicInteger rejectedReceipts = new AtomicInteger();
    private final AtomicInteger revocations = new AtomicInteger();
    private final AtomicInteger chargebacks = new AtomicInteger();
    private final AtomicInteger chargebackReversals = new AtomicInteger();
    private final AtomicInteger approvedRefunds = new AtomicInteger();
    private final AtomicInteger completedRefunds = new AtomicInteger();
    private final AtomicInteger failedRefunds = new AtomicInteger();
    private final AtomicInteger deniedReversals = new AtomicInteger();

    private final List<String> denials = new ArrayList<>();
    private String director;
    private String administrative;

    private LocalDate today() {
        return LocalDate.now(MutableClock.INSTANCE);
    }

    private void registerRandomCash(Random random, Debtor debtor) {
        String amount = (5000L * (1 + random.nextInt(30))) + ".00";
        try {
            CashPaymentPreview preview = inTransaction(() -> cashPreviewService.previewCashPaymentOfStudent(debtor.studentId(), ars(amount)));
            RegisterCashPaymentCommand command = new RegisterCashPaymentCommand(debtor.studentId(), ars(amount), null, null, null, preview.fingerprint(), false, nextKey(), actor);
            pipeline.runOperation(cashOperation, command);
            cashPayments.incrementAndGet();
        } catch (BadRequestException | ConflictException | NotFoundException expectedRejection) {
            assertThat(expectedRejection).isNotNull();
        }
    }

    private BankTransferReceiptSubmissionResult submitOldestInvoices(Random random, Debtor debtor) {
        List<Map<String, Object>> payable = jdbc.queryForList("select id, outstanding_amount from invoice where student_current_account_id = ? "
                + "and payment_status in ('PENDING', 'OVERDUE') and document_status = 'ISSUED' order by due_date, issue_date, id", debtor.accountId());
        if (payable.isEmpty()) {
            return null;
        }
        int count = 1 + random.nextInt(Math.min(2, payable.size()));
        List<Long> chosen = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < count; index++) {
            chosen.add(((Number) payable.get(index).get("id")).longValue());
            total = total.add((BigDecimal) payable.get(index).get("outstanding_amount"));
        }
        BigDecimal declared = total.multiply(BigDecimal.valueOf(60 + random.nextInt(81))).movePointLeft(2).setScale(2, java.math.RoundingMode.HALF_UP);
        if (declared.signum() <= 0) {
            return null;
        }
        try {
            return submit(command(debtor, chosen, declared.toPlainString(), today(), nextOperationNumber()));
        } catch (BadRequestException | ConflictException | NotFoundException expectedRejection) {
            return null;
        }
    }

    private void approveRandomReceipt(Random random, Debtor debtor) {
        BankTransferReceiptSubmissionResult submitted = submitOldestInvoices(random, debtor);
        if (submitted == null) {
            return;
        }
        try {
            PaymentVerification verification = random.nextInt(10) < 6 ? PaymentVerification.none()
                    : new PaymentVerification(ars((submitted.declaredAmount().value().amount().longValue() * (50 + random.nextInt(101)) / 100 + 1) + ".00"), null, null);
            approveReceipt.approve(submitted.receiptId(), staff, verification);
            approvedReceipts.incrementAndGet();
        } catch (BadRequestException | ConflictException | NotFoundException expectedRejection) {
            assertThat(expectedRejection).isNotNull();
        }
    }

    private void rejectRandomReceipt(Random random, Debtor debtor) {
        BankTransferReceiptSubmissionResult submitted = submitOldestInvoices(random, debtor);
        if (submitted != null) {
            rejectReceipt.reject(submitted.receiptId(), "Rechazo aleatorio", staff);
            rejectedReceipts.incrementAndGet();
        }
    }

    private Long randomPaymentWith(Random random, Debtor debtor, String sqlCondition) {
        List<Long> ids = jdbc.queryForList("select id from payment where student_current_account_id = ? and " + sqlCondition + " order by id", Long.class, debtor.accountId());
        return ids.isEmpty() ? null : ids.get(random.nextInt(ids.size()));
    }

    private MockHttpServletRequestBuilder revocation(long paymentId) {
        return post("/payments/{id}/approval-revocation", paymentId).header("Authorization", "Bearer " + director).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Aprobada por error\"}");
    }

    private MockHttpServletRequestBuilder chargeback(long paymentId, BigDecimal amount) {
        return post("/payments/{id}/chargebacks", paymentId).header("Authorization", "Bearer " + director).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":" + amount.toPlainString() + ",\"currency\":\"ARS\"},\"chargedBackOn\":\"" + today() + "\",\"reference\":\"BANCO-" + nextKey() + "\"}");
    }

    private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        int status = result.getResponse().getStatus();
        if (status >= 400) {
            denials.add(status + ":" + result.getResponse().getContentAsString().replaceAll(".*\"code\":\"([A-Z_]+)\".*", "$1"));
        }
        assertThat(status).as("a rejected money operation is a client error and never a server error").isLessThan(500);
        return status;
    }

    private void revokeRandomApproval(Random random, Debtor debtor) throws Exception {
        Long paymentId = randomPaymentWith(random, debtor, "status = 'APPROVED' and method_type = 'BANK_TRANSFER'");
        if (paymentId == null) {
            return;
        }
        if (statusOf(revocation(paymentId)) == 200) {
            revocations.incrementAndGet();
        } else {
            deniedReversals.incrementAndGet();
        }
    }

    private void chargeBackRandomPayment(Random random, Debtor debtor) throws Exception {
        Long paymentId = randomPaymentWith(random, debtor, "status = 'APPROVED'");
        if (paymentId == null) {
            return;
        }
        BigDecimal credit = amount("select coalesce(approved_amount, amount) from payment where id = ?", paymentId);
        BigDecimal requested = random.nextInt(10) == 0 ? credit.add(BigDecimal.ONE) : credit;
        if (statusOf(chargeback(paymentId, requested)) == 201) {
            chargebacks.incrementAndGet();
        } else {
            deniedReversals.incrementAndGet();
        }
    }

    private void reverseRandomChargeback(Random random, Debtor debtor) throws Exception {
        List<Long> open = jdbc.queryForList("select id from chargeback where student_current_account_id = ? and reversed_on is null order by id", Long.class, debtor.accountId());
        if (open.isEmpty()) {
            return;
        }
        long chargebackId = open.get(random.nextInt(open.size()));
        if (statusOf(post("/chargebacks/{id}/reversal", chargebackId).header("Authorization", "Bearer " + director)) == 200) {
            chargebackReversals.incrementAndGet();
        } else {
            deniedReversals.incrementAndGet();
        }
    }

    private void requestRandomRefund(Random random, Debtor debtor) throws Exception {
        BigDecimal credit = unallocatedCredit(debtor.accountId());
        long cents = credit.movePointRight(2).longValue();
        long requestedCents = cents <= 0 ? 10000 : Math.max(100, cents * (20 + random.nextInt(100)) / 100);
        if (statusOf(requestOnBehalf(debtor, director, BigDecimal.valueOf(requestedCents, 2).toPlainString())) == 201) {
            approvedRefunds.incrementAndGet();
        }
    }

    private void completeRandomRefund(Random random, Debtor debtor) throws Exception {
        List<Map<String, Object>> approved = jdbc.queryForList("select id, method_type from refund where student_current_account_id = ? and status = 'APPROVED' order by id", debtor.accountId());
        if (approved.isEmpty()) {
            return;
        }
        Map<String, Object> refund = approved.get(random.nextInt(approved.size()));
        long refundId = ((Number) refund.get("id")).longValue();
        if (random.nextInt(4) == 0) {
            if (statusOf(failure(refundId, administrative, "Falla del banco")) == 200) {
                failedRefunds.incrementAndGet();
            }
            return;
        }
        if (statusOf(completion(refundId, administrative, today().toString(), "REF-" + nextKey(), (String) refund.get("method_type"))) == 200) {
            completedRefunds.incrementAndGet();
        }
    }

    private void advanceTheClockAndMarkOverdueInvoices(Random random, List<Debtor> debtors) {
        MutableClock.INSTANCE.setDate(today().plusDays(10 + random.nextInt(40)));
        for (Debtor debtor : debtors) {
            for (Long invoiceId : jdbc.queryForList("select id from invoice where student_current_account_id = ? and payment_status = 'PENDING'", Long.class, debtor.accountId())) {
                overdueTransition.markAsOverdueWhenPastDueDate(invoiceId, today());
            }
        }
    }

    private void assertMoneyNeverLeavesMoreThanItEntered(Debtor debtor) {
        Long accountId = debtor.accountId();
        assertThat(jdbc.queryForList("select id from payment where student_current_account_id = ? and refunded_amount > coalesce(approved_amount, amount)", Long.class, accountId))
                .as("payment refunded for more than it collected").isEmpty();
        assertThat(jdbc.queryForList("select p.id from payment p where p.student_current_account_id = ? and p.status in ('CHARGED_BACK', 'REJECTED') and exists ("
                + "select 1 from refund r where r.payment_id = p.id and r.status = 'COMPLETED')", Long.class, accountId))
                .as("reversed payment whose money was also returned").isEmpty();
        assertThat(jdbc.queryForList("select payment_id from chargeback where student_current_account_id = ? and reversed_on is null group by payment_id having count(*) > 1", Long.class,
                accountId)).as("payment with two open chargebacks").isEmpty();
        BigDecimal refunded = amount("select coalesce(sum(amount), 0) from refund where student_current_account_id = ? and status = 'COMPLETED'", accountId);
        BigDecimal collected = amount("select coalesce(sum(coalesce(approved_amount, amount)), 0) from payment where student_current_account_id = ? "
                + "and status in ('APPROVED', 'REFUNDED', 'CHARGED_BACK', 'REJECTED')", accountId);
        assertThat(refunded).as("account refunded more than it ever collected").isLessThanOrEqualTo(collected);
        assertThat(unallocatedCredit(accountId)).isNotNegative();
    }

    @Test
    void randomPaymentSequencesNeverRefundMoreThanCollectedNorLetTheAccountDriftAndKeepEveryInvariant() throws Exception {
        director = staffToken("DIRECTOR");
        administrative = staffToken("ADMINISTRATIVO");
        Random random = new Random(SEED);
        for (int scenario = 0; scenario < SCENARIOS; scenario++) {
            MutableClock.INSTANCE.setDate(TODAY);
            Debtor first = debtor();
            Debtor second = debtor();
            List<Debtor> debtors = List.of(first, second);
            for (Debtor debtor : debtors) {
                for (int month = 0; month < 3 + random.nextInt(2); month++) {
                    invoiceOf(debtor, (20000L + 10000L * random.nextInt(12)) + ".00", MARCH_DUE.plusMonths(month));
                }
            }
            for (int step = 0; step < RANDOM_STEPS; step++) {
                Debtor target = step < 6 ? first : debtors.get(random.nextInt(debtors.size()));
                int kind = step == 0 ? 0 : step == 1 ? 1 : step == 2 ? 4 : step == 3 ? 5 : step == 4 ? 6 : step == 5 ? 7 : random.nextInt(11);
                switch (kind) {
                    case 0 -> registerRandomCash(random, target);
                    case 1 -> approveRandomReceipt(random, target);
                    case 2 -> rejectRandomReceipt(random, target);
                    case 3 -> revokeRandomApproval(random, target);
                    case 4 -> chargeBackRandomPayment(random, target);
                    case 5 -> reverseRandomChargeback(random, target);
                    case 6 -> requestRandomRefund(random, target);
                    case 7 -> completeRandomRefund(random, target);
                    case 8 -> approveRandomReceipt(random, target);
                    case 9 -> registerRandomCash(random, target);
                    default -> advanceTheClockAndMarkOverdueInvoices(random, debtors);
                }
                debtors.forEach(this::assertMoneyNeverLeavesMoreThanItEntered);
                invariants.assertAllBillingInvariantsHold();
            }
        }
        String coverage = "cash=" + cashPayments + " approved=" + approvedReceipts + " revocations=" + revocations + " chargebacks=" + chargebacks + " reversals=" + chargebackReversals
                + " refunds=" + approvedRefunds + " completed=" + completedRefunds + " denied=" + deniedReversals + " " + denials;
        assertThat(cashPayments.get()).as(coverage).isPositive();
        assertThat(approvedReceipts.get()).as(coverage).isPositive();
        assertThat(chargebacks.get()).as(coverage).isPositive();
        assertThat(chargebackReversals.get()).as(coverage).isPositive();
        assertThat(approvedRefunds.get()).as(coverage).isPositive();
        assertThat(completedRefunds.get()).as(coverage).isPositive();
        assertThat(deniedReversals.get() + revocations.get()).as(coverage).isPositive();
    }
}
