package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-089")
@Tag("D-139")
@Tag("D-140")
class OldestOverdueFirstAllocationStrategyTest {

    private final OldestOverdueFirstAllocationStrategy strategy = new OldestOverdueFirstAllocationStrategy();

    static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    static OpenInvoiceView invoice(long id, InvoiceDocumentType type, Long origin, String dueDate, String issueDate, String outstanding) {
        return new OpenInvoiceView(id, 1L, type, origin, LocalDate.parse(dueDate), LocalDate.parse(issueDate), ars(outstanding));
    }

    private List<Long> plannedIds(String credit, OpenInvoiceView... open) {
        return strategy.planFullInvoiceAllocations(ars(credit), List.of(open)).stream().map(PlannedInvoiceAllocation::invoiceId).toList();
    }

    @Test
    void theLiteralD140CaseSettlesMarchLeavesItsDebitNoteAndAprilUnpaid() {
        OpenInvoiceView march = invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "80000.00");
        OpenInvoiceView marchLateFee = invoice(2, InvoiceDocumentType.DEBIT_NOTE, 1L, "2027-03-10", "2027-03-16", "4000.00");
        OpenInvoiceView april = invoice(3, InvoiceDocumentType.INVOICE, null, "2027-04-10", "2027-04-01", "80000.00");

        List<PlannedInvoiceAllocation> plan = strategy.planFullInvoiceAllocations(ars("82000.00"), List.of(april, marchLateFee, march));

        assertThat(plan).containsExactly(new PlannedInvoiceAllocation(1L, ars("80000.00")));
    }

    @Test
    void creditCoveringTheInvoiceAndItsDebitNoteSettlesBothInOrder() {
        OpenInvoiceView march = invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "80000.00");
        OpenInvoiceView marchLateFee = invoice(2, InvoiceDocumentType.DEBIT_NOTE, 1L, "2027-03-10", "2027-03-16", "4000.00");

        assertThat(plannedIds("84000.00", marchLateFee, march)).containsExactly(1L, 2L);
    }

    @Test
    void theOldestDueDateGoesFirstWhateverTheInputOrder() {
        OpenInvoiceView may = invoice(5, InvoiceDocumentType.INVOICE, null, "2027-05-10", "2027-05-01", "10.00");
        OpenInvoiceView march = invoice(3, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "10.00");
        OpenInvoiceView april = invoice(4, InvoiceDocumentType.INVOICE, null, "2027-04-10", "2027-04-01", "10.00");

        assertThat(plannedIds("30.00", may, march, april)).containsExactly(3L, 4L, 5L);
    }

    @Test
    void sameDueDateAndTypeFallsBackToIssueDateAndThenToId() {
        OpenInvoiceView laterIssued = invoice(9, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-05", "10.00");
        OpenInvoiceView earlierIssuedHigherId = invoice(8, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "10.00");
        OpenInvoiceView earlierIssuedLowerId = invoice(7, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "10.00");

        assertThat(strategy.sortInAllocationOrder(List.of(laterIssued, earlierIssuedHigherId, earlierIssuedLowerId)))
                .extracting(OpenInvoiceView::invoiceId).containsExactly(7L, 8L, 9L);
    }

    @Test
    void theAllocationStopsAtTheFirstInvoiceThatDoesNotFitAndNeverSkipsToACheaperNewerOne() {
        OpenInvoiceView expensiveOld = invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "80000.00");
        OpenInvoiceView cheapNew = invoice(2, InvoiceDocumentType.INVOICE, null, "2027-04-10", "2027-04-01", "1000.00");

        assertThat(plannedIds("79999.99", expensiveOld, cheapNew)).isEmpty();
    }

    @Test
    void creditOfZeroPlansNothing() {
        assertThat(plannedIds("0.00", invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "10.00"))).isEmpty();
    }

    @Test
    void exactCreditSettlesTheInvoiceToTheLastCent() {
        assertThat(plannedIds("80000.00", invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "80000.00"))).containsExactly(1L);
    }

    @Test
    void oneCentLessThanTheOutstandingPlansNothing() {
        assertThat(plannedIds("79999.99", invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "80000.00"))).isEmpty();
    }

    @Test
    void anInvoiceWithNothingOutstandingIsSkippedWithoutStoppingTheAllocation() {
        OpenInvoiceView settled = invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "0.00");
        OpenInvoiceView open = invoice(2, InvoiceDocumentType.INVOICE, null, "2027-04-10", "2027-04-01", "10.00");

        assertThat(plannedIds("10.00", settled, open)).containsExactly(2L);
    }

    @Test
    void noOpenInvoicesPlansNothing() {
        assertThat(strategy.planFullInvoiceAllocations(ars("100.00"), List.of())).isEmpty();
    }

    @Test
    void theInputListIsNotModified() {
        OpenInvoiceView first = invoice(2, InvoiceDocumentType.INVOICE, null, "2027-04-10", "2027-04-01", "10.00");
        OpenInvoiceView second = invoice(1, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-01", "10.00");
        List<OpenInvoiceView> input = List.of(first, second);

        strategy.planFullInvoiceAllocations(ars("20.00"), input);

        assertThat(input).containsExactly(first, second);
    }

    @Test
    void anInvoiceGoesBeforeItsDebitNoteEvenWhenTheNoteHasTheEarlierIssueDateAndId() {
        OpenInvoiceView debitNote = invoice(1, InvoiceDocumentType.DEBIT_NOTE, 2L, "2027-03-10", "2027-03-01", "10.00");
        OpenInvoiceView invoice = invoice(2, InvoiceDocumentType.INVOICE, null, "2027-03-10", "2027-03-02", "10.00");

        assertThat(plannedIds("10.00", debitNote, invoice)).containsExactly(2L);
    }
}
