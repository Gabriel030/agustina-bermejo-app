package com.red_escuela.backend.billing.usecase.paymentgateway;

import java.time.Clock;
import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.operation.PaymentGatewayConfigChangeRecordingPort;
import com.red_escuela.backend.billing.operation.PaymentGatewayConfigSnapshot;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.TransactionBoundFileCleanup;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@UseCase
public class UploadQrImageService implements UploadQrImageUseCase {

    private final PaymentGatewayConfigRepository configRepository;
    private final PaymentGatewayConfigChangeRecordingPort changeRecordingPort;
    private final FileStorageService fileStorageService;
    private final TransactionBoundFileCleanup transactionBoundFileCleanup;
    private final Clock clock;

    public UploadQrImageService(PaymentGatewayConfigRepository configRepository,
                                PaymentGatewayConfigChangeRecordingPort changeRecordingPort,
                                FileStorageService fileStorageService,
                                TransactionBoundFileCleanup transactionBoundFileCleanup,
                                Clock clock) {
        this.configRepository = configRepository;
        this.changeRecordingPort = changeRecordingPort;
        this.fileStorageService = fileStorageService;
        this.transactionBoundFileCleanup = transactionBoundFileCleanup;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentGatewayConfigResponse upload(MultipartFile file, Person updatedBy) {
        PaymentGatewayConfig config = configRepository.findFirstByOrderByIdAsc()
                .orElseGet(PaymentGatewayConfig::new);
        PaymentGatewayConfigSnapshot before = changeRecordingPort.captureSnapshot(config);

        String previousPath = config.getQrImagePath();
        String path = fileStorageService.storeQr(file);
        transactionBoundFileCleanup.deleteIfRolledBack(path);
        if (previousPath != null) {
            transactionBoundFileCleanup.deleteAfterCommit(previousPath);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        config.setQrImagePath(path);
        config.setUpdatedAt(now);
        config.setUpdatedBy(updatedBy);

        PaymentGatewayConfig saved = configRepository.save(config);
        changeRecordingPort.recordChangeIfDifferent(before, changeRecordingPort.captureSnapshot(saved), updatedBy, now);
        return PaymentGatewayConfigResponse.from(saved, "/payment-config/qr-image");
    }
}
