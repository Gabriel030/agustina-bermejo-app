package com.red_escuela.backend.billing.service;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentReceiptPersister {
    private final InvoiceRepository invoiceRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final StudentRepository studentRepository;

    public PaymentReceiptPersister(InvoiceRepository invoiceRepository, PaymentReceiptRepository paymentReceiptRepository,
                                    StudentRepository studentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public PaymentReceipt persist(Long invoiceId, Long studentId, String filePath) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        validateOwnership(invoice, studentId);
        if (invoice.getStatus() != InvoiceStatus.PENDING && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus().name());
        }
        if (paymentReceiptRepository.existsByInvoiceIdAndStatus(invoiceId, PaymentReceiptStatus.PENDING_REVIEW)) {
            throw new PaymentReceiptAlreadyPendingException(invoiceId);
        }

        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setInvoice(invoice);
        receipt.setFilePath(filePath);
        receipt.setStatus(PaymentReceiptStatus.PENDING_REVIEW);
        receipt.setUploadedBy(studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId)).getPerson());
        return paymentReceiptRepository.save(receipt);
    }

    private void validateOwnership(Invoice invoice, Long studentId) {
        Long ownerId = invoice.getCourseEnrollment() != null ? invoice.getCourseEnrollment().getStudent().getId()
                : invoice.getProgramEnrollment() != null ? invoice.getProgramEnrollment().getStudent().getId()
                : null;
        if (ownerId != null && !ownerId.equals(studentId)) {
            throw new InvoiceNotFoundException(invoice.getId());
        }
    }
}
