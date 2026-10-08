package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult.InvoiceIssued;
import com.red_escuela.backend.billing.invoice.exception.InvoiceIssuingRejectedException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedDomainEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class InvoiceIssuingOperation implements BillingOperation<InvoiceIssuingContext, InvoiceIssued> {

    private final PolicySet<InvoiceIssuingContext> issuingPolicies;
    private final StudentCurrentAccountService accountService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator numberGenerator;
    private final Map<DiscountType, InvoiceDiscountCandidateStrategy> strategiesByType = new EnumMap<>(DiscountType.class);

    public InvoiceIssuingOperation(PolicySet<InvoiceIssuingContext> issuingPolicies, StudentCurrentAccountService accountService,
                                   InvoiceRepository invoiceRepository, InvoiceNumberGenerator numberGenerator,
                                   List<InvoiceDiscountCandidateStrategy> strategies) {
        this.issuingPolicies = issuingPolicies;
        this.accountService = accountService;
        this.invoiceRepository = invoiceRepository;
        this.numberGenerator = numberGenerator;
        strategies.forEach(strategy -> strategiesByType.put(strategy.discountType(), strategy));
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ISSUE_INVOICE;
    }

    @Override
    public Class<InvoiceIssuingContext> contextType() {
        return InvoiceIssuingContext.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(issuingPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(InvoiceIssuedDomainEvent.class);
    }

    @Override
    public void requireValidContext(InvoiceIssuingContext context) {
        issuingPolicies.evaluateRulesUntilFirstViolation(context).throwIfDenied(InvoiceIssuingRejectedException::new);
    }

    @Override
    public InvoiceIssued processOperation(InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        StudentCurrentAccount account = accountService.findOrOpenAndLockStudentCurrentAccount(request.studentId(), context.facts().offeringCurrency());
        Invoice invoice = InvoiceDraftAssembler.assembleDraftInvoice(context, account.getId());
        invoice.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), context.facts().issueDate(), request.dueDate(),
                context.facts().studentSnapshot(), request.actor());
        Invoice issued = invoiceRepository.save(invoice);
        invoiceRepository.flush();
        accountService.recordInvoiceIssuedDebit(account, issued, context.facts().issueDate());
        context.winningDiscount().ifPresent(this::markDiscountAsApplied);
        return new InvoiceIssued(issued);
    }

    @Override
    public List<InvoiceIssuedDomainEvent> domainEventsOf(InvoiceIssuingContext context, InvoiceIssued result) {
        Invoice invoice = result.invoice();
        return List.of(InvoiceIssuedDomainEvent.issuedFor(invoice.getId(), invoice.getStudentCurrentAccountId(), invoice.getInvoiceNumber().value(),
                invoice.getInvoiceType().name(), invoice.getAmounts().total(), invoice.getIssueDate(), invoice.getDueDate()));
    }

    private void markDiscountAsApplied(InvoiceDiscountCandidate winner) {
        strategiesByType.get(winner.discountType()).markDiscountAsApplied(winner);
    }
}
