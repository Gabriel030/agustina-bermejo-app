package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.identity.api.ActivationTokenIssuer;
import com.red_escuela.backend.identity.api.IssuedActivationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class InvitationActivationTokenRequester {

    private final ActivationTokenIssuer activationTokenIssuer;
    private final TransactionTemplate isolatedTransaction;

    public InvitationActivationTokenRequester(ActivationTokenIssuer activationTokenIssuer, PlatformTransactionManager transactionManager) {
        this.activationTokenIssuer = activationTokenIssuer;
        this.isolatedTransaction = new TransactionTemplate(transactionManager);
        this.isolatedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public IssuedActivationToken requestActivationToken(Long personId) {
        return isolatedTransaction.execute(status -> activationTokenIssuer.issueActivationToken(personId));
    }
}
