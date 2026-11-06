package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.issuing.DraftInvoiceApprovalCommand;
import com.red_escuela.backend.billing.invoice.service.StoredInvoiceLoader;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import com.red_escuela.backend.billing.payment.operation.IdempotencyKeyHandler;
import com.red_escuela.backend.billing.payment.operation.StoredResultMapping;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyCheck;
import com.red_escuela.backend.billing.payment.service.IdempotencyRecordService;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Configuration
public class MonthlyRunDraftReviewHandlersConfiguration {
    private static final String INVOICE_IDS_FIELD = "invoiceIds";

    @Bean
    @SuppressWarnings("unchecked")
    public OperationPreHandlers approveDraftInvoicesPreHandlers(IdempotencyRecordService recordService, ObjectMapper objectMapper, StoredInvoiceLoader invoices,
                                                                PolicySet<IdempotencyKeyCheck> keyFormatPolicies, MonthlyInvoiceRunRepository runRepository) {
        StoredResultMapping storedApproval = new StoredResultMapping(approved -> Map.of(INVOICE_IDS_FIELD, ((List<Invoice>) approved).stream().map(Invoice::getId).sorted().toList()),
                body -> invoices.loadInvoices(StreamSupport.stream(readTree(objectMapper, body).get(INVOICE_IDS_FIELD).spliterator(), false).map(JsonNode::asLong).toList()));
        return new OperationPreHandlers(BillingOperationType.APPROVE_DRAFT_INVOICES,
                List.of(new IdempotencyKeyHandler<DraftInvoiceApprovalCommand>(recordService, objectMapper, storedApproval, keyFormatPolicies),
                        new MonthlyRunAwaitingReviewHandler(runRepository)));
    }

    private static JsonNode readTree(ObjectMapper objectMapper, String body) {
        return objectMapper.readTree(body);
    }
}
