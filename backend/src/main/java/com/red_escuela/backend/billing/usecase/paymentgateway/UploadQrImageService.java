package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@UseCase
public class UploadQrImageService implements UploadQrImageUseCase {

    private final PaymentGatewayConfigRepository configRepository;
    private final FileStorageService fileStorageService;

    public UploadQrImageService(PaymentGatewayConfigRepository configRepository, FileStorageService fileStorageService) {
        this.configRepository = configRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    public PaymentGatewayConfigResponse upload(MultipartFile file, Person updatedBy) {
        PaymentGatewayConfig config = configRepository.findFirstByOrderByIdAsc()
                .orElseGet(PaymentGatewayConfig::new);

        if (config.getQrImagePath() != null) {
            fileStorageService.delete(config.getQrImagePath());
        }

        String path = fileStorageService.storeQr(file);
        config.setQrImagePath(path);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(updatedBy);

        PaymentGatewayConfig saved = configRepository.save(config);
        return PaymentGatewayConfigResponse.from(saved, "/payment-config/qr-image");
    }
}
