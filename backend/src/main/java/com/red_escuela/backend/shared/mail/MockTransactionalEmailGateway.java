package com.red_escuela.backend.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.mail.transactional", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockTransactionalEmailGateway implements TransactionalEmailGateway {

    private static final Logger log = LoggerFactory.getLogger(MockTransactionalEmailGateway.class);
    private static final String MESSAGE_ID_PREFIX = "mock-";

    @Override
    public EmailDeliveryReceipt send(TransactionalEmail email) {
        String messageId = MESSAGE_ID_PREFIX + UUID.randomUUID();
        log.info("Mail simulado {} para {}", messageId, MaskedEmailAddress.of(email.recipient()));
        return new EmailDeliveryReceipt(messageId, true);
    }
}
