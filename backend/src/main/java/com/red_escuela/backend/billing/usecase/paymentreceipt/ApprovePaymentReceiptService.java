package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.events.PaymentReceiptAppliedToOlderDebtEvent;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.payment.domain.BankTransferPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.payment.service.ApprovedPaymentCreditService;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.PaymentReceiptApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@UseCase
public class ApprovePaymentReceiptService implements ApprovePaymentReceiptUseCase {
    private static final Logger log = LoggerFactory.getLogger(ApprovePaymentReceiptService.class);
    private final BankTransferReceiptRepository bankTransferReceiptRepository;
    private final PaymentReceiptFinder paymentReceiptFinder;
    private final InvoiceRepository invoiceRepository;
    private final StudentCurrentAccountService accountService;
    private final PaymentRepository paymentRepository;
    private final ApprovedPaymentCreditService creditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ApprovePaymentReceiptService(BankTransferReceiptRepository bankTransferReceiptRepository, PaymentReceiptFinder paymentReceiptFinder,
                                        InvoiceRepository invoiceRepository, StudentCurrentAccountService accountService, PaymentRepository paymentRepository,
                                        ApprovedPaymentCreditService creditService, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.paymentReceiptFinder = paymentReceiptFinder;
        this.invoiceRepository = invoiceRepository;
        this.accountService = accountService;
        this.paymentRepository = paymentRepository;
        this.creditService = creditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BankTransferReceipt approve(Long id, Person decidedBy) {
        BankTransferReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        receipt.approve(decidedBy);

        Invoice reportedInvoice = receipt.getInvoice();
        Long invoiceId = reportedInvoice.getId();
        Long accountId = invoiceRepository.findStudentCurrentAccountIdByInvoiceId(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (!invoice.isPayable()) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getPaymentStatus() == null ? invoice.getDocumentStatus().name() : invoice.getPaymentStatus().name());
        }
        LocalDate today = LocalDate.now(clock);
        StatusChangeActor actor = StatusChangeActor.ofPerson(decidedBy.getId());
        NonNegativeMoney verifiedAmount = NonNegativeMoney.of(invoice.getAmounts().outstandingAmount().value());
        Payment payment = Payment.createPendingBankTransferPayment(account, verifiedAmount, new BankTransferPaymentDetails(null, null, null, null, null), null, actor);
        payment.approvePayment(verifiedAmount, today, actor, null);
        payment = paymentRepository.save(payment);
        Set<Long> programAdmissionInvoiceIds = invoice.getIssuingReason() != null && invoice.getIssuingReason().isProgramAdmission() ? Set.of(invoiceId) : Set.of();
        creditService.creditApprovedPaymentAndAllocate(account, payment, today, actor, programAdmissionInvoiceIds);
        receipt.setPaymentId(payment.getId());

        if (invoice.getPaymentStatus() != InvoicePaymentStatus.PAID) {
            receipt.markAsAppliedToOlderDebt();
            BankTransferReceipt applied = bankTransferReceiptRepository.save(receipt);
            if (applied.payer() != null) {
                eventPublisher.publishEvent(PaymentReceiptAppliedToOlderDebtEvent.appliedFor(applied.getId(), invoice.getId(), applied.payer().getId(),
                        invoice.getPeriod().format(DateTimeFormatter.ofPattern("MM/yyyy"))));
            }
            log.info("Comprobante aprobado y aplicado a deuda anterior: comprobante={}, factura={}", id, invoice.getId());
            return applied;
        }

        BankTransferReceipt saved = bankTransferReceiptRepository.save(receipt);
        eventPublisher.publishEvent(new PaymentReceiptApprovedEvent(saved));
        log.info("Comprobante de pago aprobado: comprobante={}, factura={}", id, invoice.getId());
        return saved;
    }
}
