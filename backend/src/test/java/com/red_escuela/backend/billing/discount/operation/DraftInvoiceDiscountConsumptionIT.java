package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.EnrollmentInvoiceQueryService;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.service.MonthlyRunDraftReviewer;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-516")
@Tag("D-052")
class DraftInvoiceDiscountConsumptionIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);

    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private EnrollmentInvoiceQueryService invoiceQueries;

    @Autowired
    private MonthlyRunDraftReviewer draftReviewer;

    private MonthlyInvoiceRun reviewRun;

    @BeforeEach
    void startReviewRun() {
        LocalDate firstDay = MARCH.atDay(1);
        reviewRun = runRepository.saveAndFlush(MonthlyInvoiceRun.startRealRunPricedAt(firstDay.withYear(2040), firstDay.withYear(2040), firstDay.withYear(2040),
                MonthlyInvoiceRunTrigger.SCHEDULED, MonthlyInvoiceIssuanceMode.REVIEW, LocalDateTime.of(2040, 3, 1, 3, 0)));
    }

    @AfterEach
    void closeReviewRun() {
        MonthlyInvoiceRun current = runRepository.findById(reviewRun.getId()).orElseThrow();
        if (current.getStatus() == MonthlyInvoiceRunStatus.RUNNING) {
            current.abortMonthlyRun("TEST_CLEANUP", LocalDateTime.of(2040, 3, 1, 4, 0));
            runRepository.saveAndFlush(current);
        } else {
            jdbc.update("update invoice set monthly_invoice_run_id = null where monthly_invoice_run_id = ?", current.getId());
            jdbc.update("delete from monthly_invoice_run_item where monthly_invoice_run_id = ?", current.getId());
            jdbc.update("delete from monthly_invoice_run where id = ?", current.getId());
        }
    }

    private Long createDraft(YearMonth period) {
        today(period.atDay(1));
        InvoiceIssuingResult result = issuingService.issueInvoiceInNewTransaction(installmentOf(learner, period), new InvoiceIssuanceDestination.DraftOfMonthlyRun(reviewRun.getId()));
        assertThat(result).isInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
        return ((InvoiceIssuingResult.InvoiceIssued) result).invoice().getId();
    }

    private void discardDraft(Long invoiceId, LocalDate on) {
        today(on);
        invoiceQueries.discardDraftInvoices(List.of(invoiceId), "Descarte de prueba", StatusChangeActor.ofProcess("TEST_DRAFT_DISCARD"));
    }

    private BigDecimal discountsTotalOf(Long invoiceId) {
        return jdbc.queryForObject("select discounts_total from invoice where id = ?", BigDecimal.class, invoiceId);
    }

    private String statusOf(Long studentDiscountId) {
        return jdbc.queryForObject("select status from student_discount where id = ?", String.class, studentDiscountId);
    }

    private long liveUsesOf(Long studentDiscountId) {
        return jdbc.queryForObject("select count(*) from invoice_discount where student_discount_id = ? and consumption_released_at is null", Long.class, studentDiscountId);
    }

    @Test
    void aDraftOfTheRunHasNoNumberNoDueDateAndNoDebitOnTheAccount() {
        Long draftId = createDraft(MARCH);

        Invoice draft = invoiceRepository.findById(draftId).orElseThrow();
        assertThat(draft.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DRAFT);
        assertThat(draft.getInvoiceNumber()).isNull();
        assertThat(draft.getDueDate()).isNull();
        assertThat(draft.getMonthlyInvoiceRunId()).isEqualTo(reviewRun.getId());
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, draft.getStudentCurrentAccountId())).isZero();
    }

    @Test
    void aSecondCreationOfTheSameChargeDoesNotDuplicateTheDraftBecauseItKeepsTheChargeKey() {
        Long draftId = createDraft(MARCH);

        InvoiceIssuingResult again = issuingService.issueInvoiceInNewTransaction(installmentOf(learner, MARCH), new InvoiceIssuanceDestination.DraftOfMonthlyRun(reviewRun.getId()));

        assertThat(again).isEqualTo(new InvoiceIssuingResult.InvoiceAlreadyIssued(draftId));
    }

    @Test
    void aDraftConsumesTheSingleUseDiscountWhenItIsCreatedAndTheNextDraftOfTheSameRunDoesNotGetItAgain() {
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 1);

        Long march = createDraft(MARCH);
        Long april = createDraft(APRIL);

        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");
        assertThat(liveUsesOf(promotionId)).isEqualTo(1L);
        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, MARCH)).hasSize(1);
        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, APRIL)).isEmpty();
        assertThat(discountsTotalOf(march)).isGreaterThan(BigDecimal.ZERO);
        assertThat(discountsTotalOf(april)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void discardingTheDraftDaysLaterReturnsTheConsumptionAndTheDiscountIsAvailableForTheNextDraft() {
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 1);
        Long march = createDraft(MARCH);
        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");

        discardDraft(march, MARCH.atDay(6));

        assertThat(invoiceRepository.findById(march).orElseThrow().getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DISCARDED);
        assertThat(statusOf(promotionId)).isEqualTo("ACTIVE");
        assertThat(liveUsesOf(promotionId)).isZero();
        Long reissued = createDraft(MARCH);
        assertThat(reissued).isNotEqualTo(march);
        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");
        List<Map<String, Object>> rows = discountsOn(FeeType.MONTHLY_INSTALLMENT, MARCH);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("consumption_released_at")).isNotNull();
        assertThat(rows.get(1).get("consumption_released_at")).isNull();
    }

    @Test
    void discardingTheDiscountedDraftThroughTheRunReviewReturnsTheConsumptionAndCompletesTheRun() {
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 1);
        Long march = createDraft(MARCH);
        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");
        MonthlyInvoiceRun awaiting = runRepository.findById(reviewRun.getId()).orElseThrow();
        awaiting.finishMonthlyRun(MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_DRAFTS_PENDING_REVIEW, LocalDateTime.of(2040, 3, 1, 3, 30));
        runRepository.saveAndFlush(awaiting);
        today(MARCH.atDay(6));

        draftReviewer.discardDraftsOfRun(reviewRun.getId(), List.of(march), "Descarte de prueba", StatusChangeActor.ofProcess("TEST_DRAFT_DISCARD"));

        assertThat(invoiceRepository.findById(march).orElseThrow().getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DISCARDED);
        assertThat(statusOf(promotionId)).isEqualTo("ACTIVE");
        assertThat(liveUsesOf(promotionId)).isZero();
        assertThat(runRepository.findById(reviewRun.getId()).orElseThrow().getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
    }
}
