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

    @Test
    void creditThatDoesNotCoverTheAdmissionInvoiceSettlesNothingEvenIfItCoversTheOlderDebt() {
        OpenInvoiceView smallerOlderDebt = invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "30000.00");
        AccountCreditAllocationStrategy strategy = new ProgramAdmissionInvoicePaidFirstAllocationStrategy(oldestFirst, Set.of(2L));

        assertThat(strategy.planFullInvoiceAllocations(ars("40000.00"), List.of(smallerOlderDebt, admission))).isEmpty();
        assertThat(oldestFirst.planFullInvoiceAllocations(ars("40000.00"), List.of(smallerOlderDebt, admission)))
                .extracting(PlannedInvoiceAllocation::invoiceId).containsExactly(1L);
    }
}
