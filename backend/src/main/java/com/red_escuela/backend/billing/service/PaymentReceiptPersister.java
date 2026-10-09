package com.red_escuela.backend.billing.service;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;

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
    private final StudentRepository studentRepository;

    public PaymentReceiptPersister(InvoiceRepository invoiceRepository, BankTransferReceiptRepository bankTransferReceiptRepository,
                                    StudentRepository studentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public BankTransferReceipt persist(Long invoiceId, Long studentId, String filePath) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        validateOwnership(invoice, studentId);
        if (!invoice.isPayable()) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getPaymentStatus() == null ? invoice.getDocumentStatus().name() : invoice.getPaymentStatus().name());
        }
        if (bankTransferReceiptRepository.existsByInvoiceIdAndStatus(invoiceId, BankTransferReceiptStatus.PENDING_REVIEW)) {
            throw new PaymentReceiptAlreadyPendingException(invoiceId);
        }

        BankTransferReceipt receipt = new BankTransferReceipt();
        receipt.setInvoice(invoice);
        receipt.setFilePath(filePath);
        receipt.setStatus(BankTransferReceiptStatus.PENDING_REVIEW);
        receipt.setUploadedBy(studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId)).getPerson());
        return bankTransferReceiptRepository.save(receipt);
    }

    private void validateOwnership(Invoice invoice, Long studentId) {
        Long ownerId = invoiceRepository.findOwnerStudentIdByInvoiceId(invoice.getId()).orElse(null);
        if (ownerId != null && !ownerId.equals(studentId)) {
            throw new InvoiceNotFoundException(invoice.getId());
        }
    }
}
