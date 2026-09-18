package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.web.multipart.MultipartFile;

public interface UploadQrImageUseCase {

    PaymentGatewayConfigResponse upload(MultipartFile file, Person updatedBy);
}
