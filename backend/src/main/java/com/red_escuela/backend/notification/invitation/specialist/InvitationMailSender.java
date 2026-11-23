package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.shared.mail.EmailDeliveryReceipt;
import com.red_escuela.backend.shared.mail.MailCircuit;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import com.red_escuela.backend.shared.resilience.Bulkhead;
import com.red_escuela.backend.shared.resilience.BulkheadFactory;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InvitationMailSender {

    private final TransactionalEmailGateway gateway;
    private final CircuitBreaker mailBreaker;
    private final Bulkhead mailBulkhead;

    @Autowired
    public InvitationMailSender(TransactionalEmailGateway gateway, CircuitBreakerFactory circuitBreakerFactory, BulkheadFactory bulkheadFactory) {
        this(gateway, circuitBreakerFactory.breakerNamed(MailCircuit.NAME), bulkheadFactory.bulkheadNamed(MailCircuit.NAME));
    }

    public InvitationMailSender(TransactionalEmailGateway gateway, CircuitBreaker mailBreaker, Bulkhead mailBulkhead) {
        this.gateway = gateway;
        this.mailBreaker = mailBreaker;
        this.mailBulkhead = mailBulkhead;
    }

    public boolean isMailChannelRejectingCalls() {
        return mailBreaker.isRejectingCalls() || mailBulkhead.availablePermits() == 0;
    }

    public EmailDeliveryReceipt sendInvitationEmail(TransactionalEmail email) {
        return Bulkhead.executeProtected(mailBulkhead, mailBreaker, () -> gateway.send(email), MailCircuit::countsAsProviderFailure);
    }
}
