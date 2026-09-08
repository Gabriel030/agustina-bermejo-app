package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.service.PaymentReceiptPersister;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.web.multipart.MultipartFile;

// studentId se recibe explícito, no se deriva de invoice.getCourseEnrollment()/getProgramEnrollment()
// — el flujo de Épica 7 Fase 3 sube el comprobante contra una Invoice ad-hoc todavía sin ningún
// enrollment vinculado, así que ese vínculo puede no existir todavía en este punto — en ese caso,
// ownership no se puede validar contra la Invoice y se confía en el caller.
//
// Sin @Transactional acá a propósito (corrección 2026-09-04, auditoría): el upload real del
// archivo es I/O potencialmente lento y no debe correr con el lock pesimista de la Invoice
// sostenido — el upload corre primero, sin transacción abierta, y PaymentReceiptPersister.persist
// (transacción propia, corta) valida todo bajo lock justo antes de persistir.
@UseCase
public class UploadPaymentReceiptService implements UploadPaymentReceiptUseCase {

    private final FileStorageService fileStorageService;
    private final PaymentReceiptPersister paymentReceiptPersister;

    public UploadPaymentReceiptService(FileStorageService fileStorageService, PaymentReceiptPersister paymentReceiptPersister) {
        this.fileStorageService = fileStorageService;
        this.paymentReceiptPersister = paymentReceiptPersister;
    }

    @Override
    public PaymentReceipt upload(Long invoiceId, Long studentId, MultipartFile comprobante) {
        String filePath = fileStorageService.store(studentId, "comprobantes", comprobante);
        return paymentReceiptPersister.persist(invoiceId, studentId, filePath);
    }
}
