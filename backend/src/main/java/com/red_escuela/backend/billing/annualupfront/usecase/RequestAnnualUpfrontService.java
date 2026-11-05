package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.RegisteredAnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.service.RequestAnnualUpfrontCommandAssembler;
import com.red_escuela.backend.billing.annualupfront.service.RequestAnnualUpfrontOperation;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class RequestAnnualUpfrontService implements RequestAnnualUpfrontUseCase {
    private final BillingOperationPipeline pipeline;
    private final RequestAnnualUpfrontOperation operation;
    private final RequestAnnualUpfrontCommandAssembler commandAssembler;

    public RequestAnnualUpfrontService(BillingOperationPipeline pipeline, RequestAnnualUpfrontOperation operation, RequestAnnualUpfrontCommandAssembler commandAssembler) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.commandAssembler = commandAssembler;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public RegisteredAnnualUpfrontRequest requestAnnualUpfrontForCurrentStudent(Long programEnrollmentId, boolean termsAccepted, String termsVersion, String idempotencyKey) {
        return pipeline.runOperation(operation, commandAssembler.assembleCommandOfCurrentStudent(programEnrollmentId, termsAccepted, termsVersion, idempotencyKey))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
