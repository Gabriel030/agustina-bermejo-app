package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.OnlineOnlyInvoiceResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenOnlineInvoicesCounterTest {

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final OnlineOnlyInvoiceResolver resolver = mock(OnlineOnlyInvoiceResolver.class);
    private final OpenOnlineInvoicesCounter counter = new OpenOnlineInvoicesCounter(invoiceRepository, resolver);

    @Test
    void countsOnlyTheOpenInvoicesOfOnlineOnlyOfferings() {
        List<Invoice> open = List.of(mock(Invoice.class), mock(Invoice.class), mock(Invoice.class));
        when(invoiceRepository.findAllOpenIssuedWithLines()).thenReturn(open);
        when(resolver.findInvoiceIdsOfOnlineOnlyOfferings(open)).thenReturn(Set.of(10L, 30L));

        assertThat(counter.countOpenOnlineDependencies()).isEqualTo(2);
    }

    @Test
    void returnsZeroWhenThereAreNoOpenInvoices() {
        when(invoiceRepository.findAllOpenIssuedWithLines()).thenReturn(List.of());
        when(resolver.findInvoiceIdsOfOnlineOnlyOfferings(List.of())).thenReturn(Set.of());

        assertThat(counter.countOpenOnlineDependencies()).isZero();
    }
}
