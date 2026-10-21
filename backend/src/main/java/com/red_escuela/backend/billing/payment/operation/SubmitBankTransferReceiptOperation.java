package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.BankTransferReceiptSubmittedEvent;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.BankTransferPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptRecord;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSelectionPlanner;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentCheckoutItem;
import com.red_escuela.backend.billing.payment.domain.PlannedCheckoutItem;
import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptDetailsCheck;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptRegistrationCheck;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptRejections;
import com.red_escuela.backend.billing.payment.policy.CashPaymentCheck;
import com.red_escuela.backend.billing.payment.policy.CashPaymentRejections;
import com.red_escuela.backend.billing.payment.policy.ReceiptProtectionCheck;
import com.red_escuela.backend.billing.payment.repository.BankTransferReceiptRecordRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentCheckoutItemRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.identity.api.StudentExistenceLookup;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.storage.TransactionBoundFileCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SubmitBankTransferReceiptOperation implements BillingOperation<SubmitBankTransferReceiptCommand, BankTransferReceiptSubmissionResult> {
    private static final Logger log = LoggerFactory.getLogger(SubmitBankTransferReceiptOperation.class);
    private static final String DUPLICATE_INDEX_NAME = "uk_payment_bank_transfer_duplicate";
    private static final String UNDER_REVIEW_REASON = "Comprobante de transferencia subido a tiempo";

    private final PolicySet<CashPaymentCheck> idempotencyKeyPolicies;
    private final PolicySet<BankTransferReceiptDetailsCheck> detailsPolicies;
    private final PolicySet<BankTransferReceiptRegistrationCheck> registrationPolicies;
    private final PolicySet<ReceiptProtectionCheck> protectionPolicies;
    private final StudentExistenceLookup students;
    private final StudentCurrentAccountService accountService;
    private final InvoiceAllocationQueryService invoiceQueries;
    private final PaymentRepository payments;
    private final PaymentCheckoutItemRepository checkoutItems;
    private final BankTransferReceiptRecordRepository receipts;
    private final TransactionBoundFileCleanup fileCleanup;
    private final Clock clock;

    public SubmitBankTransferReceiptOperation(PolicySet<CashPaymentCheck> bankTransferReceiptIdempotencyKeyPolicySet,
                                              PolicySet<BankTransferReceiptDetailsCheck> bankTransferReceiptDetailsPolicySet,
                                              PolicySet<BankTransferReceiptRegistrationCheck> bankTransferReceiptRegistrationPolicySet,
                                              PolicySet<ReceiptProtectionCheck> bankTransferReceiptProtectionPolicySet, StudentExistenceLookup students,
                                              StudentCurrentAccountService accountService, InvoiceAllocationQueryService invoiceQueries, PaymentRepository payments,
                                              PaymentCheckoutItemRepository checkoutItems, BankTransferReceiptRecordRepository receipts, TransactionBoundFileCleanup fileCleanup,
                                              Clock clock) {
        this.fileCleanup = fileCleanup;
        this.idempotencyKeyPolicies = bankTransferReceiptIdempotencyKeyPolicySet;
        this.detailsPolicies = bankTransferReceiptDetailsPolicySet;
        this.registrationPolicies = bankTransferReceiptRegistrationPolicySet;
        this.protectionPolicies = bankTransferReceiptProtectionPolicySet;
        this.students = students;
        this.accountService = accountService;
        this.invoiceQueries = invoiceQueries;
        this.payments = payments;
        this.checkoutItems = checkoutItems;
        this.receipts = receipts;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.SUBMIT_BANK_TRANSFER_RECEIPT;
    }

    @Override
    public Class<SubmitBankTransferReceiptCommand> contextType() {
        return SubmitBankTransferReceiptCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(idempotencyKeyPolicies.moment(), detailsPolicies.moment(), registrationPolicies.moment(), protectionPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(BankTransferReceiptSubmittedEvent.class);
    }

    @Override
    public void requireValidContext(SubmitBankTransferReceiptCommand command) {
        idempotencyKeyPolicies.evaluateRulesUntilFirstViolation(new CashPaymentCheck(null, null, null, false, null, null, command.idempotencyKey()))
                .throwIfDenied(CashPaymentRejections::rejectionOf);
        detailsPolicies.evaluateRulesUntilFirstViolation(new BankTransferReceiptDetailsCheck(command.declaredAmount(), command.transferDate(), LocalDate.now(clock),
                command.operationNumber(), command.originAccount(), command.holderName(), command.bankName(), command.hasFile(),
                new HashSet<>(command.invoiceIds()).size())).throwIfDenied(BankTransferReceiptRejections::rejectionOf);
    }

    @Override
    public BankTransferReceiptSubmissionResult processOperation(SubmitBankTransferReceiptCommand command) {
        LocalDate today = LocalDate.now(clock);
        students.requireExistingStudent(command.studentId());
        Currency currency = command.declaredAmount().currency();
        StudentCurrentAccount account = accountService.findOrOpenAndLockStudentCurrentAccount(command.studentId(), currency);
        Set<Long> selectedIds = new TreeSet<>(command.invoiceIds());
        invoiceQueries.lockOpenInvoicesOfAccountInIdOrder(account.getId());
        Map<Long, Invoice> openById = invoiceQueries.listOpenInvoicesOfAccount(account.getId()).stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
        Map<Long, Invoice> selectedById = selectedInvoicesById(selectedIds, openById);
        String operationNumber = command.operationNumber().strip();
        BankTransferPaymentDetails details = new BankTransferPaymentDetails(textOrNull(command.bankName()), command.originAccount().strip(), operationNumber,
                command.holderName().strip(), command.transferDate());
        NonNegativeMoney declaredAmount = NonNegativeMoney.of(command.declaredAmount());
        boolean duplicateExists = payments.existsActiveBankTransfer(operationNumber, command.transferDate().toString(), declaredAmount.value().amount());
        boolean legacyReceiptPending = receipts.existsLegacyPendingReceiptOnAnyInvoice(selectedById.keySet());
        registrationPolicies.evaluateRulesUntilFirstViolation(new BankTransferReceiptRegistrationCheck(account.getId(), currency,
                selectedById.values().stream().map(SubmitBankTransferReceiptOperation::factOf).toList(),
                openById.values().stream().map(SubmitBankTransferReceiptOperation::factOf).toList(), duplicateExists, legacyReceiptPending))
                .throwIfDenied(BankTransferReceiptRejections::rejectionOf);
        List<ReceiptInvoiceFact> selectedFacts = selectedById.values().stream().map(SubmitBankTransferReceiptOperation::factOf).toList();
        List<PlannedCheckoutItem> plan = BankTransferReceiptSelectionPlanner.planItems(selectedFacts, command.declaredAmount(), today, command.transferDate(),
                facts -> protectionPolicies.evaluateRulesUntilFirstViolation(new ReceiptProtectionCheck(facts)).allowed());
        String filePath = command.fileStorage().storeReceiptFileOf(command.studentId());
        fileCleanup.deleteIfRolledBack(filePath);
        Payment payment = savePendingPayment(account, declaredAmount, details, command);
        List<PaymentCheckoutItem> items = checkoutItems.saveAll(plan.stream().map(planned -> PaymentCheckoutItem.of(payment.getId(), account.getId(), planned)).toList());
        plan.stream().filter(PlannedCheckoutItem::protectedFromOverdue)
                .forEach(planned -> selectedById.get(planned.invoiceId()).markPaymentUnderReview(today, command.actor(), UNDER_REVIEW_REASON));
        BankTransferReceiptRecord receipt = receipts.save(BankTransferReceiptRecord.submittedFor(plan.get(0).invoiceId(), filePath, command.actor().personId(),
                payment.getId(), declaredAmount, details));
        log.info("Comprobante de transferencia subido: comprobante={}, pago={}, cuenta={}, monto={}, facturas={}, protegidas={}, actor={}", receipt.getId(),
                payment.getId(), account.getId(), declaredAmount.value().amount(), plan.stream().map(PlannedCheckoutItem::invoiceId).toList(),
                plan.stream().filter(PlannedCheckoutItem::protectedFromOverdue).map(PlannedCheckoutItem::invoiceId).toList(), command.actor().personId());
        return new BankTransferReceiptSubmissionResult(receipt.getId(), payment.getId(), account.getId(), declaredAmount, today,
                items.stream().map(item -> new BankTransferReceiptSubmissionResult.Item(item.getInvoiceId(), item.getPosition(), item.getOutstandingAtSelection(),
                        item.isMarkedUnderReview())).toList());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(SubmitBankTransferReceiptCommand command, BankTransferReceiptSubmissionResult result) {
        return List.of(BankTransferReceiptSubmittedEvent.submittedFor(result.receiptId(), result.paymentId(), result.studentCurrentAccountId(), result.declaredAmount(),
                result.items().stream().map(BankTransferReceiptSubmissionResult.Item::invoiceId).toList()));
    }

    private Map<Long, Invoice> selectedInvoicesById(Set<Long> selectedIds, Map<Long, Invoice> openById) {
        List<Long> notOpenIds = selectedIds.stream().filter(id -> !openById.containsKey(id)).toList();
        Map<Long, Invoice> selected = new LinkedHashMap<>();
        selectedIds.stream().filter(openById::containsKey).forEach(id -> selected.put(id, openById.get(id)));
        if (!notOpenIds.isEmpty()) {
            Map<Long, Invoice> found = invoiceQueries.findInvoicesByIds(notOpenIds).stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
            List<Long> missing = new ArrayList<>(notOpenIds);
            missing.removeAll(found.keySet());
            if (!missing.isEmpty()) {
                throw new InvoiceNotFoundException(missing.get(0));
            }
            selected.putAll(found);
        }
        return selected;
    }

    private Payment savePendingPayment(StudentCurrentAccount account, NonNegativeMoney declaredAmount, BankTransferPaymentDetails details,
                                       SubmitBankTransferReceiptCommand command) {
        try {
            return payments.save(Payment.createPendingBankTransferPayment(account, declaredAmount, details, command.idempotencyKey(), command.actor()));
        } catch (DataIntegrityViolationException violation) {
            if (String.valueOf(violation.getMostSpecificCause().getMessage()).contains(DUPLICATE_INDEX_NAME)) {
                throw BankTransferReceiptRejections.duplicateDetectedByUniqueIndex();
            }
            throw violation;
        }
    }

    private static ReceiptInvoiceFact factOf(Invoice invoice) {
        return new ReceiptInvoiceFact(invoice.getId(), invoice.getStudentCurrentAccountId(), invoice.getCurrency(),
                invoice.getDocumentStatus() == InvoiceDocumentStatus.ISSUED, invoice.getPaymentStatus(),
                invoice.getDueDate(), invoice.getAmounts().outstandingAmount().value());
    }

    private static String textOrNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
