package com.red_escuela.backend.billing.usecase.latepaymentsettings;

import com.red_escuela.backend.billing.configuration.policy.CreateLatePaymentSettingsCommand;
import com.red_escuela.backend.billing.configuration.service.CreateLatePaymentSettingsOperation;
import com.red_escuela.backend.billing.dto.CreateLatePaymentSettingsRequest;
import com.red_escuela.backend.billing.entity.LatePaymentSettings;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateLatePaymentSettingsService implements CreateLatePaymentSettingsUseCase {

    private final BillingOperationPipeline pipeline;
    private final CreateLatePaymentSettingsOperation operation;
    private final CurrentPersonService currentPersonService;

    public CreateLatePaymentSettingsService(BillingOperationPipeline pipeline, CreateLatePaymentSettingsOperation operation,
                                          CurrentPersonService currentPersonService) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.currentPersonService = currentPersonService;
    }

    @Override
    public LatePaymentSettings create(CreateLatePaymentSettingsRequest request) {
        CreateLatePaymentSettingsCommand command = new CreateLatePaymentSettingsCommand(request.gracePeriodDays(), request.surchargePercent(),
                request.suspensionAfterDays(), request.suspensionAfterInvoiceCount(), request.effectiveFrom(),
                currentPersonService.resolveCurrentPerson().getId());
        return pipeline.runOperation(operation, command).resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
