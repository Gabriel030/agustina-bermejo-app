package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import com.red_escuela.backend.billing.account.service.AllocationSourceQueryService;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditNoteSourceShare;
import com.red_escuela.backend.billing.note.exception.CreditNoteSourceIntegrityException;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.payment.domain.PaymentFixtures.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllocationSourceInvoiceCreditNoteShareSourceTest {

    private final AllocationSourceQueryService sources = mock(AllocationSourceQueryService.class);
    private final CreditNoteRepository creditNotes = mock(CreditNoteRepository.class);
    private final AllocationSourceInvoiceCreditNoteShareSource shareSource = new AllocationSourceInvoiceCreditNoteShareSource(sources, creditNotes);

    private static PaymentAllocationSource creditNoteSource(long creditNoteId, String amount) {
        PaymentAllocationSource source = mock(PaymentAllocationSource.class);
        when(source.getPaymentId()).thenReturn(null);
        when(source.getCreditNoteId()).thenReturn(creditNoteId);
        when(source.getAmount()).thenReturn(ars(amount));
        return source;
    }

    private static PaymentAllocationSource paymentSource(long paymentId, String amount) {
        PaymentAllocationSource source = mock(PaymentAllocationSource.class);
        when(source.getPaymentId()).thenReturn(paymentId);
        when(source.getCreditNoteId()).thenReturn(null);
        when(source.getAmount()).thenReturn(ars(amount));
        return source;
    }

    private void givenSources(long invoiceId, PaymentAllocationSource... invoiceSources) {
        when(sources.findCurrentSourcesByInvoiceId(any())).thenReturn(Map.of(invoiceId, List.of(invoiceSources)));
    }

    private void givenCreditNoteNumbers(Object... idsAndNumbers) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < idsAndNumbers.length; i += 2) {
            rows.add(new Object[]{idsAndNumbers[i], idsAndNumbers[i + 1]});
        }
        when(creditNotes.findCreditNoteNumbersByIds(any())).thenReturn(rows);
    }

    @Test
    @Tag("D-376")
    void creditNoteSourcesAreListedPerCreditNoteWithTheirNumberAndSummedPerNote() {
        givenSources(1L, creditNoteSource(9L, "1000.00"), creditNoteSource(10L, "500.00"), creditNoteSource(9L, "250.00"), paymentSource(31L, "700.00"));
        givenCreditNoteNumbers(9L, "NC-0000009", 10L, "NC-0000010");

        List<InvoiceCreditNoteSourceShare> shares = shareSource.findCurrentCreditNoteSharesByInvoiceId(List.of(1L)).get(1L);

        assertThat(shares).extracting(InvoiceCreditNoteSourceShare::creditNoteId, InvoiceCreditNoteSourceShare::creditNoteNumber, share -> share.amountApplied().amount().toPlainString())
                .containsExactly(tuple(9L, "NC-0000009", "1250.00"), tuple(10L, "NC-0000010", "500.00"));
    }

    @Test
    @Tag("D-376")
    void anInvoiceFinancedOnlyByPaymentsHasNoCreditNoteShares() {
        givenSources(1L, paymentSource(31L, "700.00"));

        assertThat(shareSource.findCurrentCreditNoteSharesByInvoiceId(List.of(1L))).isEmpty();
    }

    @Test
    @Tag("D-376")
    void aCreditNoteSourceWhoseNoteDoesNotExistFailsInsteadOfBeingDropped() {
        givenSources(1L, creditNoteSource(9L, "1000.00"), creditNoteSource(404L, "1000.00"));
        givenCreditNoteNumbers(9L, "NC-0000009");

        assertThatThrownBy(() -> shareSource.findCurrentCreditNoteSharesByInvoiceId(List.of(1L)))
                .isInstanceOf(CreditNoteSourceIntegrityException.class)
                .hasMessageContaining("404");
    }
}
