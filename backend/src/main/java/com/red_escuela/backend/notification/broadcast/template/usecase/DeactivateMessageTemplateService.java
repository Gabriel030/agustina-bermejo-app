package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.service.DeactivateMessageTemplateTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeactivateMessageTemplateService implements DeactivateMessageTemplateUseCase {

    private final DeactivateMessageTemplateTransaction transaction;

    public DeactivateMessageTemplateService(DeactivateMessageTemplateTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public void deactivateMessageTemplate(Long id) {
        transaction.deactivateMessageTemplate(id);
    }
}
