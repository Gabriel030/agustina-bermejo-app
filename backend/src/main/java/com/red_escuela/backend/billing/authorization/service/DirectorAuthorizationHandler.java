package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.PreProcessingHandler;
import com.red_escuela.backend.billing.operation.PreProcessingResult;
import com.red_escuela.backend.billing.operation.PriorAuthorization;
import org.springframework.stereotype.Component;

@Component
public class DirectorAuthorizationHandler implements PreProcessingHandler<DirectorApprovable> {

    @Override
    public PreProcessingResult handleBeforeProcessing(BillingOperationType type, DirectorApprovable context, PriorAuthorization priorAuthorization) {
        if (priorAuthorization == PriorAuthorization.APPROVED || context.requestedByDirector()) {
            return PreProcessingResult.proceed();
        }
        return PreProcessingResult.requireAuthorizationUntil(AuthorizationRole.DIRECTOR, context.authorizationReason(), context.authorizationExpiresOn());
    }
}
