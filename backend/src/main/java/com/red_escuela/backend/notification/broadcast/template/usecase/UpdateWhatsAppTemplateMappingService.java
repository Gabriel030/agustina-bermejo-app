package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingResponse;
import com.red_escuela.backend.notification.broadcast.template.service.SaveWhatsAppTemplateMappingTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateWhatsAppTemplateMappingService implements UpdateWhatsAppTemplateMappingUseCase {

    private final CurrentActorFinder currentActorFinder;
    private final SaveWhatsAppTemplateMappingTransaction transaction;

    public UpdateWhatsAppTemplateMappingService(CurrentActorFinder currentActorFinder, SaveWhatsAppTemplateMappingTransaction transaction) {
        this.currentActorFinder = currentActorFinder;
        this.transaction = transaction;
    }

    @Override
    public WhatsAppTemplateMappingResponse updateMapping(Long id, WhatsAppTemplateMappingRequest request) {
        return transaction.updateMapping(id, request, currentActorFinder.currentPersonId());
    }
}
