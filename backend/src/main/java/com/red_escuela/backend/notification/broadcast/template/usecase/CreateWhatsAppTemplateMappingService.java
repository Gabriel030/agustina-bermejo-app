package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingResponse;
import com.red_escuela.backend.notification.broadcast.template.service.SaveWhatsAppTemplateMappingTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateWhatsAppTemplateMappingService implements CreateWhatsAppTemplateMappingUseCase {

    private final CurrentActorFinder currentActorFinder;
    private final SaveWhatsAppTemplateMappingTransaction transaction;

    public CreateWhatsAppTemplateMappingService(CurrentActorFinder currentActorFinder, SaveWhatsAppTemplateMappingTransaction transaction) {
        this.currentActorFinder = currentActorFinder;
        this.transaction = transaction;
    }

    @Override
    public WhatsAppTemplateMappingResponse createMapping(WhatsAppTemplateMappingRequest request) {
        return transaction.createMapping(request, currentActorFinder.currentPersonId());
    }
}
