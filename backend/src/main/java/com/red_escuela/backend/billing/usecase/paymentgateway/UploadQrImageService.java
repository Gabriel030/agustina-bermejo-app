package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.payment.service.UploadQrImageTransaction;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.web.multipart.MultipartFile;

@UseCase
public class UploadQrImageService implements UploadQrImageUseCase {

    private final UploadQrImageTransaction uploadTransaction;

    public UploadQrImageService(UploadQrImageTransaction uploadTransaction) {
        this.uploadTransaction = uploadTransaction;
    }

    @Override
    public PaymentGatewayConfigResponse upload(MultipartFile file, Person updatedBy) {
        return uploadTransaction.upload(file, updatedBy);
    }
}
