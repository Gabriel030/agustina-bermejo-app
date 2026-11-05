package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.operation.AuthorizationRequestObserver;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.InvoiceTargetedRequest;
import org.springframework.stereotype.Component;

@Component
public class LinkInvoicesToOpenedRequestObserver implements AuthorizationRequestObserver {
    private final AuthorizationRequestRepository requests;

    public LinkInvoicesToOpenedRequestObserver(AuthorizationRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    public void afterOpened(Long requestId, BillingOperationType type, Object context) {
        if (context instanceof InvoiceTargetedRequest targeted) {
            targeted.targetedInvoiceIds().forEach(invoiceId -> requests.linkInvoiceToRequest(requestId, invoiceId));
        }
    }
}
