package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;
import com.red_escuela.backend.notification.broadcast.template.service.CreateMessageTemplateTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateMessageTemplateService implements CreateMessageTemplateUseCase {

    private final MessageTemplateContentValidator contentValidator;
    private final CurrentActorFinder currentActorFinder;
    private final CreateMessageTemplateTransaction transaction;

    public CreateMessageTemplateService(MessageTemplateContentValidator contentValidator,
                                        CurrentActorFinder currentActorFinder,
                                        CreateMessageTemplateTransaction transaction) {
        this.contentValidator = contentValidator;
        this.currentActorFinder = currentActorFinder;
        this.transaction = transaction;
    }

    @Override
    public MessageTemplateResponse createMessageTemplate(MessageTemplateRequest request) {
        contentValidator.validateContent(request);
        return transaction.createMessageTemplate(request, currentActorFinder.currentPersonId());
    }
}
