package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.DiscardAnnualUpfrontRequestCommandAssembler;
import com.red_escuela.backend.billing.annualupfront.service.DiscardAnnualUpfrontRequestOperation;
import com.red_escuela.backend.billing.annualupfront.service.DiscardedAnnualUpfrontRequest;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class DiscardAnnualUpfrontRequestService implements DiscardAnnualUpfrontRequestUseCase {
    private final BillingOperationPipeline pipeline;
    private final DiscardAnnualUpfrontRequestOperation operation;
    private final DiscardAnnualUpfrontRequestCommandAssembler commandAssembler;

    public DiscardAnnualUpfrontRequestService(BillingOperationPipeline pipeline, DiscardAnnualUpfrontRequestOperation operation, DiscardAnnualUpfrontRequestCommandAssembler commandAssembler) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.commandAssembler = commandAssembler;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public DiscardedAnnualUpfrontRequest discardAnnualUpfrontRequestOfCurrentStudent(Long requestId) {
        return pipeline.runOperation(operation, commandAssembler.assembleCommandOfCurrentStudent(requestId)).resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
