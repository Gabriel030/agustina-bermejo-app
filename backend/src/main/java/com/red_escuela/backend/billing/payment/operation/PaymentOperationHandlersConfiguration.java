package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.authorization.service.DirectorAuthorizationHandler;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyCheck;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.billing.payment.service.IdempotencyRecordService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class PaymentOperationHandlersConfiguration {
    private static final String REFUND_REQUEST_ID_FIELD = "refundRequestId";
    private static final String CREDIT_NOTE_ID_FIELD = "creditNoteId";

    @Bean
    public OperationPreHandlers registerPaymentPreHandlers(IdempotencyRecordService recordService, ObjectMapper objectMapper) {
        return new OperationPreHandlers(BillingOperationType.REGISTER_PAYMENT,
                List.of(new IdempotencyKeyHandler<RegisterCashPaymentCommand>(recordService, objectMapper, CashPaymentRegistrationResult.class)));
    }

    @Bean
    public OperationPreHandlers issueCreditNotePreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler, IdempotencyRecordService recordService,
                                                           ObjectMapper objectMapper, CreditNoteRepository creditNotes, PolicySet<IdempotencyKeyCheck> keyFormatPolicies) {
        StoredResultMapping storedNote = new StoredResultMapping(note -> Map.of(CREDIT_NOTE_ID_FIELD, ((CreditNote) note).getId()),
                body -> creditNotes.findWithLinesById(StoredResultMapping.storedId(objectMapper, body, CREDIT_NOTE_ID_FIELD)).orElseThrow());
        return new OperationPreHandlers(BillingOperationType.ISSUE_CREDIT_NOTE,
                List.of(new IdempotencyKeyHandler<IssueCreditNoteCommand>(recordService, objectMapper, storedNote, keyFormatPolicies), directorAuthorizationHandler));
    }

    @Bean
    @SuppressWarnings("unchecked")
    public OperationPreHandlers issueRefundPreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler, IdempotencyRecordService recordService,
                                                       ObjectMapper objectMapper, RefundRepository refunds,
                                                       PolicySet<IdempotencyKeyCheck> keyFormatPolicies) {
        StoredResultMapping storedParts = new StoredResultMapping(
                parts -> Map.of(REFUND_REQUEST_ID_FIELD, ((List<Refund>) parts).get(0).getRefundRequestId()),
                body -> refunds.findByRefundRequestIdOrderByIdAsc(StoredResultMapping.storedId(objectMapper, body, REFUND_REQUEST_ID_FIELD)));
        return new OperationPreHandlers(BillingOperationType.ISSUE_REFUND,
                List.of(new IdempotencyKeyHandler<IssueRefundCommand>(recordService, objectMapper, storedParts, keyFormatPolicies), directorAuthorizationHandler));
    }

    @Bean
    public OperationPreHandlers submitBankTransferReceiptPreHandlers(IdempotencyRecordService recordService, ObjectMapper objectMapper) {
        return new OperationPreHandlers(BillingOperationType.SUBMIT_BANK_TRANSFER_RECEIPT,
                List.of(new IdempotencyKeyHandler<SubmitBankTransferReceiptCommand>(recordService, objectMapper, BankTransferReceiptSubmissionResult.class)));
    }
}
