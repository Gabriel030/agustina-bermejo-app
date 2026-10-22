package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.red_escuela.backend.billing.account.domain.OldestOverdueFirstAllocationStrategyTest.ars;
import static com.red_escuela.backend.billing.account.domain.OldestOverdueFirstAllocationStrategyTest.invoice;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-329")
class ProgramAdmissionInvoicePaidFirstAllocationStrategyTest {

    private final AccountCreditAllocationStrategy oldestFirst = new OldestOverdueFirstAllocationStrategy();
    private final OpenInvoiceView march = invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "80000.00");
    private final OpenInvoiceView admission = invoice(2, InvoiceDocumentType.INVOICE, null, "2027-04-20", "2027-04-01", "50000.00");

    private List<Long> plannedIds(AccountCreditAllocationStrategy strategy, String credit) {
        return strategy.planFullInvoiceAllocations(ars(credit), List.of(march, admission)).stream().map(PlannedInvoiceAllocation::invoiceId).toList();
    }

    @Test
    void thePaidAdmissionInvoiceIsSettledBeforeTheOlderOverdueDebt() {
        AccountCreditAllocationStrategy strategy = new ProgramAdmissionInvoicePaidFirstAllocationStrategy(oldestFirst, Set.of(2L));

        assertThat(plannedIds(strategy, "50000.00")).containsExactly(2L);
    }

    @Test
    void creditLeftOverAfterTheAdmissionInvoiceFlowsToTheOldestDebt() {
        AccountCreditAllocationStrategy strategy = new ProgramAdmissionInvoicePaidFirstAllocationStrategy(oldestFirst, Set.of(2L));

        assertThat(plannedIds(strategy, "130000.00")).containsExactly(2L, 1L);
        assertThat(strategy.sortInAllocationOrder(List.of(march, admission))).extracting(OpenInvoiceView::invoiceId).containsExactly(2L, 1L);
    }

    @Test
    void aPaymentThatIsNotForAnAdmissionInvoiceStillGoesToTheOldestOverdueDebt() {
        AccountCreditAllocationStrategy strategy = new ProgramAdmissionInvoicePaidFirstAllocationStrategy(oldestFirst, Set.of());

        assertThat(plannedIds(strategy, "80000.00")).containsExactly(1L);
        assertThat(plannedIds(oldestFirst, "80000.00")).containsExactly(1L);
    }

    private Set<Long> covered(Set<Long> candidates, String credit, OpenInvoiceView... invoices) {
        return ProgramAdmissionInvoicePaidFirstAllocationStrategy.programAdmissionInvoiceIdsFullyCoveredByCredit(oldestFirst, candidates, ars(credit), List.of(invoices));
    }

    @Test
    @Tag("D-089")
    void creditThatDoesNotCoverTheAdmissionInvoiceCoversNoAdmissionAndTheOlderDebtGoesFirst() {
        OpenInvoiceView smallerOlderDebt = invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "30000.00");

        assertThat(covered(Set.of(2L), "40000.00", smallerOlderDebt, admission)).isEmpty();
        assertThat(covered(Set.of(2L), "49999.99", smallerOlderDebt, admission)).isEmpty();
        assertThat(covered(Set.of(2L), "50000.00", smallerOlderDebt, admission)).containsExactly(2L);
        assertThat(oldestFirst.planFullInvoiceAllocations(ars("40000.00"), List.of(smallerOlderDebt, admission)))
                .extracting(PlannedInvoiceAllocation::invoiceId).containsExactly(1L);
    }

    @Test
    void creditIsAccumulatedPerAdmissionInvoiceAndStopsAtTheFirstOneItDoesNotCover() {
        OpenInvoiceView secondAdmission = invoice(3, InvoiceDocumentType.INVOICE, null, "2027-05-20", "2027-05-01", "50000.00");
        OpenInvoiceView thirdAdmission = invoice(4, InvoiceDocumentType.INVOICE, null, "2027-06-20", "2027-06-01", "10000.00");

        assertThat(covered(Set.of(2L, 3L, 4L), "80000.00", march, admission, secondAdmission, thirdAdmission)).containsExactly(2L);
        assertThat(covered(Set.of(2L, 3L, 4L), "100000.00", march, admission, secondAdmission, thirdAdmission)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(covered(Set.of(2L, 3L, 4L), "110000.00", march, admission, secondAdmission, thirdAdmission)).containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void onlyDeclaredAdmissionInvoicesWithOutstandingBalanceAreCovered() {
        OpenInvoiceView alreadySettledAdmission = invoice(3, InvoiceDocumentType.INVOICE, null, "2027-03-01", "2027-03-01", "0.00");

        assertThat(covered(Set.of(2L, 3L), "50000.00", march, admission, alreadySettledAdmission)).containsExactly(2L);
        assertThat(covered(Set.of(), "500000.00", march, admission)).isEmpty();
    }
}
