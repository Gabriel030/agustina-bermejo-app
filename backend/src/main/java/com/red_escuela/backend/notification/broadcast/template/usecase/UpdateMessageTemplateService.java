package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;
import com.red_escuela.backend.notification.broadcast.template.service.UpdateMessageTemplateTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateMessageTemplateService implements UpdateMessageTemplateUseCase {

    private final MessageTemplateContentValidator contentValidator;
    private final UpdateMessageTemplateTransaction transaction;

    public UpdateMessageTemplateService(MessageTemplateContentValidator contentValidator,
                                        UpdateMessageTemplateTransaction transaction) {
        this.contentValidator = contentValidator;
        this.transaction = transaction;
    }

    @Override
    public MessageTemplateResponse updateMessageTemplate(UpdateMessageTemplateCommand command) {
        contentValidator.validateContent(command.request());
        return transaction.updateMessageTemplate(command.id(), command.request());
    }
}
