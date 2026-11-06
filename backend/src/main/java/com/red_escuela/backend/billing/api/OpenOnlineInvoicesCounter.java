package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.OnlineOnlyInvoiceResolver;
import com.red_escuela.backend.shared.module.OnlinePaymentDependencyPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OpenOnlineInvoicesCounter implements OnlinePaymentDependencyPort {

    private final InvoiceRepository invoiceRepository;
    private final OnlineOnlyInvoiceResolver onlineOnlyInvoiceResolver;

    public OpenOnlineInvoicesCounter(InvoiceRepository invoiceRepository, OnlineOnlyInvoiceResolver onlineOnlyInvoiceResolver) {
        this.invoiceRepository = invoiceRepository;
        this.onlineOnlyInvoiceResolver = onlineOnlyInvoiceResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenOnlineDependencies() {
        return onlineOnlyInvoiceResolver.findInvoiceIdsOfOnlineOnlyOfferings(invoiceRepository.findAllOpenIssuedWithLines()).size();
    }
}
