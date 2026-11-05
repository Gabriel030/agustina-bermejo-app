package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.run.domain.DraftInvoiceGroup;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.billing.run.service.MonthlyRunDraftInvoiceGrouper;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-513")
@Tag("D-514")
@Tag("D-052")
class MonthlyInvoiceRunReviewModeIT extends AbstractInvoiceSequenceScenarioTest {

    @Autowired
    private MonthlyInvoiceRunExecutor executor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private MonthlyInvoiceRunItemRepository itemRepository;
    @Autowired
    private MonthlyRunDraftInvoiceGrouper grouper;

    @BeforeEach
    @AfterEach
    void forgetRunsAndDetachTheirInvoices() {
        jdbcTemplate.update("update invoice set monthly_invoice_run_id = null where monthly_invoice_run_id is not null");
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    private Long enrollmentStartingIn(java.time.LocalDate firstDay) {
        var career = newCareer();
        var year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(firstDay);
        return newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 4), date(2026, 3, 10));
    }

    private MonthlyInvoiceRun runOf(java.time.LocalDate firstDay, MonthlyInvoiceIssuanceMode mode) {
        job.completeTheNightOf(firstDay);
        return executor.runMonthlyInvoicesOfPeriodPricedAt(YearMonth.from(firstDay), firstDay, firstDay, MonthlyInvoiceRunTrigger.SCHEDULED, mode).orElseThrow();
    }

    private Money netTotalOfLines(List<Long> invoiceIds, FeeType feeType) {
        BigDecimal total = jdbcTemplate.queryForObject("select coalesce(sum(l.amount - l.discount_amount), 0) from invoice_line l where l.invoice_id in ("
                + String.join(",", invoiceIds.stream().map(String::valueOf).toList()) + ") and l.fee_type = ?", BigDecimal.class, feeType.name());
        return Money.of(total, Currency.ARS);
    }

    private List<Long> invoiceIdsOf(MonthlyInvoiceRun run) {
        return itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream().flatMap(item -> item.getInvoiceIds().stream()).toList();
    }

    @Test
    void aReviewRunLeavesDraftsWithoutNumberAndWaitsForReviewWithIssuedItems() {
        Long enrollment = enrollmentStartingIn(date(2026, 4, 1));

        MonthlyInvoiceRun run = runOf(date(2026, 4, 1), MonthlyInvoiceIssuanceMode.REVIEW);

        assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(MonthlyInvoiceRunStatus.AWAITING_REVIEW);
        MonthlyInvoiceRunItem item = itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream().filter(candidate -> candidate.getEnrollmentId().equals(enrollment)).findFirst().orElseThrow();
        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
        assertThat(item.getInvoiceIds()).isNotEmpty();
        assertThat(jdbcTemplate.queryForList("select document_status from invoice where id in (" + String.join(",", item.getInvoiceIds().stream().map(String::valueOf).toList()) + ")", String.class))
                .containsOnly("DRAFT");
    }

    @Test
    void draftsAreGroupedByProgramAndFeeTypeWithoutDifferenceWhenThereIsNoPreviousMonth() {
        enrollmentStartingIn(date(2026, 4, 1));
        MonthlyInvoiceRun run = runOf(date(2026, 4, 1), MonthlyInvoiceIssuanceMode.REVIEW);

        List<DraftInvoiceGroup> groups = grouper.groupDraftsOfRunComparedWithPreviousPeriod(run);

        DraftInvoiceGroup installments = groups.stream().filter(group -> group.key().feeType() == FeeType.MONTHLY_INSTALLMENT).findFirst().orElseThrow();
        assertThat(installments.draftCount()).isEqualTo(1);
        assertThat(installments.draftTotal()).isEqualTo(netTotalOfLines(invoiceIdsOf(run), FeeType.MONTHLY_INSTALLMENT));
        assertThat(installments.findPreviousIssuedTotal()).isEmpty();
        assertThat(installments.findDifferenceFromPreviousPeriod()).isEmpty();
        assertThat(installments.findPercentageDifferenceFromPreviousPeriod()).isEmpty();
    }

    @Test
    void theDifferenceIsMeasuredAgainstTheIssuedInvoicesOfTheSameGroupInThePreviousMonth() {
        enrollmentStartingIn(date(2026, 4, 1));
        MonthlyInvoiceRun april = runOf(date(2026, 4, 1), MonthlyInvoiceIssuanceMode.DIRECT);
        assertThat(april.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        today(date(2026, 5, 1));
        MonthlyInvoiceRun may = runOf(date(2026, 5, 1), MonthlyInvoiceIssuanceMode.REVIEW);

        DraftInvoiceGroup installments = grouper.groupDraftsOfRunComparedWithPreviousPeriod(may).stream()
                .filter(group -> group.key().feeType() == FeeType.MONTHLY_INSTALLMENT).findFirst().orElseThrow();

        Money aprilTotal = netTotalOfLines(invoiceIdsOf(april), FeeType.MONTHLY_INSTALLMENT);
        Money mayTotal = netTotalOfLines(invoiceIdsOf(may), FeeType.MONTHLY_INSTALLMENT);
        assertThat(installments.findPreviousIssuedTotal()).contains(aprilTotal);
        assertThat(installments.draftTotal()).isEqualTo(mayTotal);
        assertThat(installments.findDifferenceFromPreviousPeriod()).contains(mayTotal.minus(aprilTotal));
    }
}
