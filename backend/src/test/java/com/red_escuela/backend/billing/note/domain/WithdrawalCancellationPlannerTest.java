package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.header;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.issue;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.withId;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-204")
@Tag("D-248")
@Tag("D-300")
@Tag("D-312")
class WithdrawalCancellationPlannerTest {
    private static final long TEMPLATE_2028 = 7L;
    private static final LocalDate START_2028 = LocalDate.of(2028, 3, 1);

    private WithdrawalCancellationPlanner plannerFor(YearMonth withdrawalMonth, LocalDate registeredOn) {
        return new WithdrawalCancellationPlanner(withdrawalMonth, registeredOn, templateId -> templateId == TEMPLATE_2028 ? Optional.of(START_2028) : Optional.empty());
    }

    private Invoice monthly(long id, YearMonth period, boolean issued) {
        InvoiceLineDraft line = new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota " + period, period, 123L, null, null, null, 5L, "PE:123:M:" + period, ars("80000.00"));
        Invoice invoice = InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT), line, Optional.empty(), ACTOR);
        if (issued) {
            issue(invoice);
        }
        return withId(invoice, id);
    }

    private Invoice annual(long id, YearMonth from, int months) {
        List<InvoiceLineDraft> lines = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth period = from.plusMonths(i);
            lines.add(new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota " + period, period, 123L, null, null, null, 5L, "PE:123:A:" + period, ars("80000.00")));
        }
        Invoice invoice = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header(InvoiceType.ANNUAL_UPFRONT), lines, Map.of(), ACTOR);
        issue(invoice);
        return withId(invoice, id);
    }

    private Invoice enrollmentFee(long id) {
        InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ENROLLMENT_FEE, "Matrícula 2028", YearMonth.of(2028, 2), 123L, null, null, null, 5L, "PE:123:F:2028",
                ars("50000.00"), TEMPLATE_2028, 1);
        Invoice invoice = InvoiceFactory.createEnrollmentFeeInvoiceDraft(header(InvoiceType.ENROLLMENT_FEE), line, Optional.empty(), ACTOR);
        issue(invoice);
        return withId(invoice, id);
    }

    @Test
    @Tag("C-09")
    void onlyTheInstallmentsAfterTheWithdrawalMonthEnterTheRequest() {
        List<Invoice> invoices = List.of(monthly(1, YearMonth.of(2027, 5), true), monthly(2, YearMonth.of(2027, 6), true), monthly(3, YearMonth.of(2027, 7), true));

        WithdrawalCancellationPlan plan = plannerFor(YearMonth.of(2027, 5), LocalDate.of(2027, 5, 15)).planFor(invoices, Set.of());

        assertThat(plan.requestItems()).extracting(WithdrawalCancellationItem::invoiceId).containsExactly(2L, 3L);
        assertThat(plan.requestItems()).allMatch(item -> item.kind() == WithdrawalCancellationKind.CANCEL_INVOICE);
        assertThat(plan.automaticInvoiceIds()).isEmpty();
    }

    @Test
    @Tag("C-13")
    void anUnpaidAnnualInvoiceIsReducedOnlyByTheLinesAfterTheWithdrawalMonth() {
        Invoice annual = annual(9, YearMonth.of(2027, 3), 10);

        WithdrawalCancellationPlan plan = plannerFor(YearMonth.of(2027, 5), LocalDate.of(2027, 5, 15)).planFor(List.of(annual), Set.of());

        assertThat(plan.requestItems()).hasSize(1);
        WithdrawalCancellationItem item = plan.requestItems().get(0);
        assertThat(item.kind()).isEqualTo(WithdrawalCancellationKind.REDUCE_LINES);
        assertThat(item.lines()).hasSize(7).allMatch(line -> line.amount().equals(ars("80000.00").value()));
        assertThat(annual.getLines().stream().filter(line -> item.lines().stream().anyMatch(reduced -> reduced.lineNumber() == line.getLineNumber())).map(line -> line.getPeriod()))
                .containsExactlyInAnyOrder(YearMonth.of(2027, 6), YearMonth.of(2027, 7), YearMonth.of(2027, 8), YearMonth.of(2027, 9), YearMonth.of(2027, 10),
                        YearMonth.of(2027, 11), YearMonth.of(2027, 12));
    }

    @Test
    @Tag("C-13")
    void anAnnualInvoiceWhoseEveryLineIsAfterTheWithdrawalIsCancelledWhole() {
        WithdrawalCancellationPlan plan = plannerFor(YearMonth.of(2027, 2), LocalDate.of(2027, 2, 10)).planFor(List.of(annual(9, YearMonth.of(2027, 3), 10)), Set.of());

        assertThat(plan.requestItems()).containsExactly(WithdrawalCancellationItem.cancelling(9L));
    }

    @Test
    @Tag("C-14")
    void draftsOfLaterMonthsAreDiscardedAndTheWithdrawalMonthDraftIsKept() {
        List<Invoice> invoices = List.of(monthly(1, YearMonth.of(2027, 5), false), monthly(2, YearMonth.of(2027, 6), false), monthly(3, YearMonth.of(2027, 7), false));

        WithdrawalCancellationPlan plan = plannerFor(YearMonth.of(2027, 5), LocalDate.of(2027, 5, 15)).planFor(invoices, Set.of());

        assertThat(plan.draftInvoiceIds()).containsExactly(2L, 3L);
        assertThat(plan.requestItems()).isEmpty();
    }

    @Test
    @Tag("C-12")
    void anInvoiceThatAlreadyHasAPendingRequestIsNotRequestedAgain() {
        WithdrawalCancellationPlan plan = plannerFor(YearMonth.of(2027, 5), LocalDate.of(2027, 5, 15))
                .planFor(List.of(monthly(2, YearMonth.of(2027, 6), true), monthly(3, YearMonth.of(2027, 7), true)), Set.of(2L));

        assertThat(plan.requestItems()).extracting(WithdrawalCancellationItem::invoiceId).containsExactly(3L);
    }

    @Test
    @Tag("C-34")
    void theNextYearEnrollmentFeeIsCancelledByTheSystemWhileTheYearHasNotStarted() {
        Invoice fee = enrollmentFee(4);

        assertThat(plannerFor(YearMonth.of(2027, 11), LocalDate.of(2027, 11, 20)).planFor(List.of(fee), Set.of()).automaticInvoiceIds()).containsExactly(4L);
        assertThat(plannerFor(YearMonth.of(2028, 2), LocalDate.of(2028, 2, 29)).planFor(List.of(fee), Set.of()).automaticInvoiceIds()).containsExactly(4L);
    }

    @Test
    @Tag("C-34")
    void theEnrollmentFeeStaysAsDebtFromTheFirstDayOfTheTerm() {
        Invoice fee = enrollmentFee(4);

        assertThat(plannerFor(YearMonth.of(2028, 3), LocalDate.of(2028, 3, 1)).planFor(List.of(fee), Set.of()).isEmpty()).isTrue();
        assertThat(plannerFor(YearMonth.of(2028, 3), LocalDate.of(2028, 3, 10)).planFor(List.of(fee), Set.of()).isEmpty()).isTrue();
    }

    @Test
    void theEnrollmentFeeOfAnUnknownTemplateIsNeverTouched() {
        WithdrawalCancellationPlanner planner = new WithdrawalCancellationPlanner(YearMonth.of(2027, 11), LocalDate.of(2027, 11, 20), templateId -> Optional.empty());

        assertThat(planner.planFor(List.of(enrollmentFee(4)), Set.of()).isEmpty()).isTrue();
    }
}
