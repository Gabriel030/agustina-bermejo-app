package com.red_escuela.backend.billing.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

@Component
public class AllocateAccountCreditWhenInvoiceRequestIsClosedObserver implements AuthorizationRequestObserver {
    private static final Logger log = LoggerFactory.getLogger(AllocateAccountCreditWhenInvoiceRequestIsClosedObserver.class);
    private static final Set<BillingOperationType> INVOICE_REQUEST_TYPES = Set.of(BillingOperationType.ISSUE_CREDIT_NOTE, BillingOperationType.CANCEL_INVOICE,
            BillingOperationType.CANCEL_INVOICES_AFTER_WITHDRAWAL);
    private static final String PROCESS_NAME = "INVOICE_REQUEST_CLOSED_ALLOCATION";

    private final AuthorizationRequestRepository requests;
    private final InvoiceCreditAccessService invoices;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate independentTransaction;
    private final Clock clock;

    public AllocateAccountCreditWhenInvoiceRequestIsClosedObserver(AuthorizationRequestRepository requests, InvoiceCreditAccessService invoices,
                                                                   StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                                                   ObjectMapper objectMapper, PlatformTransactionManager transactionManager, Clock clock) {
        this.requests = requests;
        this.invoices = invoices;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.objectMapper = objectMapper;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

    @Override
    public void afterResumeRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        if (!INVOICE_REQUEST_TYPES.contains(type)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            allocateWithoutBlockingTheRejection(requestId, type);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                allocateWithoutBlockingTheRejection(requestId, type);
            }
        });
    }

    private void allocateWithoutBlockingTheRejection(Long requestId, BillingOperationType type) {
        try {
            independentTransaction.executeWithoutResult(status -> allocateCreditOfTheInvoiceAccount(requestId, type));
        } catch (RuntimeException failure) {
            log.error("No se pudo imputar el saldo a favor tras rechazarse al reanudar el pedido {}; el rechazo queda registrado", requestId, failure);
        }
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
            JsonNode root = objectMapper.readTree(payload);
            return root.has("invoiceId") ? root.path("invoiceId").asLong() : root.path("items").path(0).path("invoiceId").asLong();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored operation context cannot be read for the authorization request " + requestId, e);
        }
    }
}
