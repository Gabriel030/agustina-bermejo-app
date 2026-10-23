package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult.InvoiceIssued;
import com.red_escuela.backend.billing.invoice.exception.InvoiceIssuingRejectedException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

@Component
public class InvoiceIssuingOperation implements BillingOperation<InvoiceIssuingContext, InvoiceIssued> {

    private final PolicySet<InvoiceIssuingContext> issuingPolicies;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator numberGenerator;
    private final InvoiceDiscountStrategiesByOrigin strategiesByOrigin;

    public InvoiceIssuingOperation(PolicySet<InvoiceIssuingContext> issuingPolicies, StudentCurrentAccountService accountService,
                                   CurrentAccountAllocationService allocationService, InvoiceRepository invoiceRepository, InvoiceNumberGenerator numberGenerator,
                                   List<InvoiceDiscountCandidateStrategy> strategies) {
        this.issuingPolicies = issuingPolicies;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.invoiceRepository = invoiceRepository;
        this.numberGenerator = numberGenerator;
        this.strategiesByOrigin = new InvoiceDiscountStrategiesByOrigin(strategies);
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
        return List.of(InvoiceIssuedDomainEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(InvoiceIssuingContext context) {
        issuingPolicies.evaluateRulesUntilFirstViolation(context).throwIfDenied(InvoiceIssuingRejectedException::new);
    }

    @Override
    public InvoiceIssued processOperation(InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        context.reselectDiscounts();
        context.replaceWinningDiscounts(strategiesByOrigin.registerWinnersBeforeInvoiceIsAssembled(context.winningDiscounts(), context));
        StudentCurrentAccount account = accountService.findOrOpenAndLockStudentCurrentAccount(request.studentId(), context.facts().offeringCurrency());
        Invoice invoice = InvoiceDraftAssembler.assembleDraftInvoice(context, account.getId());
        invoice.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), context.facts().issueDate(), request.dueDate(),
                context.facts().studentSnapshot(), request.actor());
        Invoice issued = invoiceRepository.save(invoice);
        invoiceRepository.flush();
        accountService.recordInvoiceIssuedDebit(account, issued, context.facts().issueDate());
        allocationService.allocateAccountCreditToOldestInvoices(account, context.facts().issueDate(), request.actor());
        strategiesByOrigin.markWinnersAsApplied(context.winningDiscounts().values());
        return new InvoiceIssued(issued);
    }

    @Override
    public List<DomainEvent> domainEventsOf(InvoiceIssuingContext context, InvoiceIssued result) {
        return result.invoice().pullRecordedDomainEvents().stream().flatMap(event -> toPublishedEvent(event, result.invoice()).stream()).toList();
    }

    private Optional<DomainEvent> toPublishedEvent(Object recorded, Invoice invoice) {
        return switch (recorded) {
            case InvoiceIssuedEvent issued -> Optional.of(InvoiceIssuedDomainEvent.issuedFor(issued.invoiceId(), issued.studentCurrentAccountId(),
                    invoice.getInvoiceNumber().value(), issued.invoiceType().name(), issued.total(), issued.issueDate(), issued.dueDate()));
            case InvoicePaidEvent paid when paid.cause() != InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED -> Optional.of(InvoicePaidDomainEvent.paidFor(paid.invoiceId(), paid.studentCurrentAccountId(), paid.cause().name(),
                    paid.businessDate()));
            default -> Optional.empty();
        };
    }
}
