package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import org.springframework.web.multipart.MultipartFile;

public interface UploadPaymentReceiptUseCase {
    BankTransferReceipt upload(Long invoiceId, Long studentId, MultipartFile comprobante);
}
