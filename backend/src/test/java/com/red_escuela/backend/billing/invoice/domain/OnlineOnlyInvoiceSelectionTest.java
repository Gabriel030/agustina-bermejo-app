package com.red_escuela.backend.billing.invoice.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

class OnlineOnlyInvoiceSelectionTest {

    private static int sequence;

    private Invoice invoiceOf(long invoiceId, Long programEnrollmentId, Long courseEnrollmentId) {
        sequence++;
        Invoice invoice = InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT),
                InvoicePrimaryLineAccessorsTest.draftOf(programEnrollmentId, courseEnrollmentId, "K" + sequence), Optional.empty(), ACTOR);
        return withId(invoice, invoiceId);
    }

    private Invoice programInvoice(long invoiceId, long programEnrollmentId) {
        return invoiceOf(invoiceId, programEnrollmentId, null);
    }

    private Invoice courseInvoice(long invoiceId, long courseEnrollmentId) {
        return invoiceOf(invoiceId, null, courseEnrollmentId);
    }

    @Test
    void programEnrollmentIdsAreCollectedAcrossInvoicesWithoutRepeats() {
        List<Invoice> invoices = List.of(programInvoice(1L, 10L), programInvoice(2L, 10L), programInvoice(3L, 11L));

        assertThat(OnlineOnlyInvoiceSelection.programEnrollmentIdsOf(invoices)).containsExactly(10L, 11L);
    }

    @Test
    void courseEnrollmentIdsAreCollectedAcrossInvoicesAndLinesWithoutEnrollmentAreSkipped() {
        List<Invoice> invoices = List.of(courseInvoice(1L, 20L), courseInvoice(2L, 21L), invoiceOf(3L, null, null));

        assertThat(OnlineOnlyInvoiceSelection.courseEnrollmentIdsOf(invoices)).containsExactly(20L, 21L);
    }

    @Test
    void anInvoiceWhoseEveryOfferingIsOnlineOnlyIsSelected() {
        Invoice onlyProgram = programInvoice(1L, 10L);
        Invoice onlyCourse = courseInvoice(2L, 20L);

        assertThat(OnlineOnlyInvoiceSelection.invoiceIdsOfOnlineOnlyOfferings(List.of(onlyProgram, onlyCourse), Set.of(10L), Set.of(20L))).containsExactly(1L, 2L);
    }

    @Test
    void aProgramEnrollmentThatIsNotOnlineOnlyExcludesTheInvoice() {
        assertThat(OnlineOnlyInvoiceSelection.invoiceIdsOfOnlineOnlyOfferings(List.of(programInvoice(1L, 10L)), Set.of(99L), Set.of(10L))).isEmpty();
    }

    @Test
    void aCourseEnrollmentThatIsNotOnlineOnlyExcludesTheInvoice() {
        assertThat(OnlineOnlyInvoiceSelection.invoiceIdsOfOnlineOnlyOfferings(List.of(courseInvoice(1L, 20L)), Set.of(20L), Set.of(99L))).isEmpty();
    }

    @Test
    void anInvoiceWithSeveralLinesOfTheSameOnlineOnlyOfferingIsSelected() {
        List<InvoiceLineDraft> drafts = List.of(InvoicePrimaryLineAccessorsTest.draftOf(10L, null, "KM1"), InvoicePrimaryLineAccessorsTest.draftOf(10L, null, "KM2"));
        Invoice mixed = withId(InvoiceFactory.createAnnualUpfrontInvoiceDraft(header(InvoiceType.ANNUAL_UPFRONT), drafts, Map.of(), ACTOR), 1L);

        assertThat(OnlineOnlyInvoiceSelection.invoiceIdsOfOnlineOnlyOfferings(List.of(mixed), Set.of(10L), Set.of(20L))).containsExactly(1L);
    }

    @Test
    void anInvoiceWithoutAnyOfferingIsNeverSelected() {
        Invoice noOffering = invoiceOf(1L, null, null);

        assertThat(OnlineOnlyInvoiceSelection.invoiceIdsOfOnlineOnlyOfferings(List.of(noOffering), Set.of(123L), Set.of())).isEmpty();
    }

    @Test
    void courseOnlyInvoicesContributeNoProgramEnrollmentIds() {
        assertThat(OnlineOnlyInvoiceSelection.programEnrollmentIdsOf(List.of(courseInvoice(1L, 20L)))).isEmpty();
    }
}
