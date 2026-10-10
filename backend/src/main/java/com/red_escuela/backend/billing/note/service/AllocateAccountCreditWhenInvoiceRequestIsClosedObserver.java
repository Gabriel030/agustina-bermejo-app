package com.red_escuela.backend.billing.note.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.operation.AuthorizationRequestObserver;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

@Component
public class AllocateAccountCreditWhenInvoiceRequestIsClosedObserver implements AuthorizationRequestObserver {
    private static final Logger log = LoggerFactory.getLogger(AllocateAccountCreditWhenInvoiceRequestIsClosedObserver.class);
    private static final Set<BillingOperationType> INVOICE_REQUEST_TYPES = Set.of(BillingOperationType.ISSUE_CREDIT_NOTE, BillingOperationType.CANCEL_INVOICE);
    private static final String PROCESS_NAME = "INVOICE_REQUEST_CLOSED_ALLOCATION";

    private final AuthorizationRequestRepository requests;
    private final InvoiceCreditAccessService invoices;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AllocateAccountCreditWhenInvoiceRequestIsClosedObserver(AuthorizationRequestRepository requests, InvoiceCreditAccessService invoices,
                                                                   StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                                                   ObjectMapper objectMapper, Clock clock) {
        this.requests = requests;
        this.invoices = invoices;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void afterRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        allocateCreditOfTheInvoiceAccount(requestId, type);
    }

    @Override
    public void afterWithdrawn(Long requestId, BillingOperationType type, Long requesterId) {
        allocateCreditOfTheInvoiceAccount(requestId, type);
    }

    private void allocateCreditOfTheInvoiceAccount(Long requestId, BillingOperationType type) {
        if (!INVOICE_REQUEST_TYPES.contains(type)) {
            return;
        }
        Long invoiceId = invoiceIdOf(requestId);
        invoices.findAccountIdOf(invoiceId).ifPresent(accountId -> {
            StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
            LocalDate businessDate = LocalDate.now(clock);
            int allocations = allocationService.allocateAccountCreditToOldestInvoices(account, businessDate, StatusChangeActor.ofProcess(PROCESS_NAME)).allocations().size();
            log.info("Saldo a favor imputado al cerrarse un pedido sobre la factura: authorizationRequestId={}, factura={}, imputaciones={}", requestId, invoiceId, allocations);
        });
    }

    private Long invoiceIdOf(Long requestId) {
        String payload = requests.findById(requestId).orElseThrow().getOperationPayload();
        try {
            return objectMapper.readTree(payload).path("invoiceId").asLong();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored operation context cannot be read for the authorization request " + requestId, e);
        }
    }
}
