package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.InvoiceNotPayableException;
import com.red_escuela.backend.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentReceiptRepository;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean separado de PaymentReceiptService a propósito (corrección 2026-09-04, auditoría): el
// upload real del archivo (I/O potencialmente lento) no debe correr con el lock pesimista de la
// Invoice sostenido — PaymentReceiptService.subir hace el upload SIN transacción abierta y recién
// después llama acá, a una transacción nueva y corta que re-valida todo bajo lock justo antes de
// persistir (el mismo motivo por el que InvoiceOverdueRowProcessor es un bean aparte: un método
// @Transactional de esta misma clase, llamado como this.metodo() desde PaymentReceiptService, no
// pasaría por el proxy de Spring).
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
