package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.service.PaymentReceiptPersister;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.web.multipart.MultipartFile;

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
