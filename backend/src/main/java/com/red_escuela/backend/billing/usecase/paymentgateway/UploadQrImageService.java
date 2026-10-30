package com.red_escuela.backend.billing.usecase.paymentgateway;

import java.time.Clock;
import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfigChange;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigChangeRepository;
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
    private final PaymentGatewayConfigChangeRepository changeRepository;
    private final FileStorageService fileStorageService;
    private final TransactionBoundFileCleanup transactionBoundFileCleanup;
    private final Clock clock;

    public UploadQrImageService(PaymentGatewayConfigRepository configRepository,
                                PaymentGatewayConfigChangeRepository changeRepository,
                                FileStorageService fileStorageService,
                                TransactionBoundFileCleanup transactionBoundFileCleanup,
                                Clock clock) {
        this.configRepository = configRepository;
        this.changeRepository = changeRepository;
        this.fileStorageService = fileStorageService;
        this.transactionBoundFileCleanup = transactionBoundFileCleanup;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentGatewayConfigResponse upload(MultipartFile file, Person updatedBy) {
        PaymentGatewayConfig config = configRepository.findFirstByOrderByIdAsc()
                .orElseGet(PaymentGatewayConfig::new);

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
        PaymentGatewayConfigChange change = new PaymentGatewayConfigChange();
        change.setChangedAt(now);
        change.setChangedBy(updatedBy);
        change.setMpEnabledBefore(saved.isMpEnabled());
        change.setMpEnabledAfter(saved.isMpEnabled());
        change.setBankAliasBefore(saved.getBankAlias());
        change.setBankAliasAfter(saved.getBankAlias());
        change.setBankCbuBefore(saved.getBankCbu());
        change.setBankCbuAfter(saved.getBankCbu());
        change.setAccountHolderBefore(saved.getAccountHolder());
        change.setAccountHolderAfter(saved.getAccountHolder());
        change.setQrImagePathBefore(previousPath);
        change.setQrImagePathAfter(path);
        changeRepository.save(change);
        return PaymentGatewayConfigResponse.from(saved, "/payment-config/qr-image");
    }
}
