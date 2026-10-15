package com.red_escuela.backend.shared.mail;

public interface TransactionalEmailGateway {

    EmailDeliveryReceipt send(TransactionalEmail email);
}
