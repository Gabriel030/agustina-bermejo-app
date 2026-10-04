package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.dto.UpdatePaymentGatewayConfigRequest;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import com.red_escuela.backend.billing.usecase.paymentgateway.GetPaymentGatewayConfigUseCase;
import com.red_escuela.backend.billing.usecase.paymentgateway.UpdatePaymentGatewayConfigUseCase;
import com.red_escuela.backend.billing.usecase.paymentgateway.UploadQrImageUseCase;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.storage.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/payment-config")
public class PaymentGatewayConfigController {

    private final GetPaymentGatewayConfigUseCase getConfigUseCase;
    private final UpdatePaymentGatewayConfigUseCase updateConfigUseCase;
    private final UploadQrImageUseCase uploadQrImageUseCase;
    private final CurrentPersonService currentPersonService;
    private final PaymentGatewayConfigRepository configRepository;
    private final FileStorageService fileStorageService;

    public PaymentGatewayConfigController(
            GetPaymentGatewayConfigUseCase getConfigUseCase,
            UpdatePaymentGatewayConfigUseCase updateConfigUseCase,
            UploadQrImageUseCase uploadQrImageUseCase,
            CurrentPersonService currentPersonService,
            PaymentGatewayConfigRepository configRepository,
            FileStorageService fileStorageService) {
        this.getConfigUseCase = getConfigUseCase;
        this.updateConfigUseCase = updateConfigUseCase;
        this.uploadQrImageUseCase = uploadQrImageUseCase;
        this.currentPersonService = currentPersonService;
        this.configRepository = configRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public PaymentGatewayConfigResponse getConfig() {
        return getConfigUseCase.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public PaymentGatewayConfigResponse updateConfig(@RequestBody UpdatePaymentGatewayConfigRequest request) {
        return updateConfigUseCase.update(request, currentPersonService.resolveCurrentPerson());
    }

    @PostMapping("/qr")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public PaymentGatewayConfigResponse uploadQr(@RequestPart MultipartFile qr) {
        return uploadQrImageUseCase.upload(qr, currentPersonService.resolveCurrentPerson());
    }

    @GetMapping(value = "/qr-image", produces = { MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE })
    public byte[] getQrImage() {
        PaymentGatewayConfig config = configRepository.findFirstByOrderByIdAsc().orElse(null);
        if (config == null || config.getQrImagePath() == null) {
            return new byte[0];
        }
        return fileStorageService.readBytes(config.getQrImagePath());
    }
}
