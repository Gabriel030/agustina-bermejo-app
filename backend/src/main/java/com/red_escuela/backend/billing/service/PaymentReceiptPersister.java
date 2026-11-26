package com.red_escuela.backend.billing.service;
import com.red_escuela.backend.identity.api.StudentFinder;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentReceiptPersister {
    private final InvoiceRepository invoiceRepository;
    private final BankTransferReceiptRepository bankTransferReceiptRepository;
    private final StudentFinder studentFinder;

    public PaymentReceiptPersister(InvoiceRepository invoiceRepository, BankTransferReceiptRepository bankTransferReceiptRepository,
                                    StudentFinder studentFinder) {
        this.invoiceRepository = invoiceRepository;
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.studentFinder = studentFinder;
    }

    @Transactional(readOnly = true)
    public void assertUploadable(Long invoiceId, Long studentId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        validateOwnership(invoice, studentId);
        requirePayable(invoice);
    }

    @Transactional
    public BankTransferReceipt persist(Long invoiceId, Long studentId, String filePath) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        validateOwnership(invoice, studentId);
        requirePayable(invoice);
        if (bankTransferReceiptRepository.existsByInvoiceIdAndStatus(invoiceId, BankTransferReceiptStatus.PENDING_REVIEW)) {
            throw new PaymentReceiptAlreadyPendingException(invoiceId);
        }

        BankTransferReceipt receipt = new BankTransferReceipt();
        receipt.setInvoice(invoice);
        receipt.setFilePath(filePath);
        receipt.setStatus(BankTransferReceiptStatus.PENDING_REVIEW);
        receipt.setUploadedBy(studentFinder.requireById(studentId).getPerson());
        return bankTransferReceiptRepository.save(receipt);
    }

    private void requirePayable(Invoice invoice) {
        if (!invoice.isPayable()) {
            throw new InvoiceNotPayableException(invoice.getId(), invoice.getPaymentStatus() == null ? invoice.getDocumentStatus().name() : invoice.getPaymentStatus().name());
        }
    }

    private void validateOwnership(Invoice invoice, Long studentId) {
        Long ownerId = invoiceRepository.findOwnerStudentIdByInvoiceId(invoice.getId()).orElse(null);
        if (!studentId.equals(ownerId)) {
            throw new InvoiceNotFoundException(invoice.getId());
        }
    }
}
