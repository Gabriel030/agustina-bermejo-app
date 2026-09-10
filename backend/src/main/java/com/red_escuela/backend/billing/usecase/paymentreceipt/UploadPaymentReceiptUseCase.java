package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import org.springframework.web.multipart.MultipartFile;

public interface UploadPaymentReceiptUseCase {
    PaymentReceipt upload(Long invoiceId, Long studentId, MultipartFile comprobante);
}
