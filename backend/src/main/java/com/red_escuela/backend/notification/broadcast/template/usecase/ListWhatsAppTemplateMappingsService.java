package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingResponse;
import com.red_escuela.backend.notification.broadcast.template.service.SaveWhatsAppTemplateMappingTransaction;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListWhatsAppTemplateMappingsService implements ListWhatsAppTemplateMappingsUseCase {

    private final SaveWhatsAppTemplateMappingTransaction transaction;

    public ListWhatsAppTemplateMappingsService(SaveWhatsAppTemplateMappingTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public List<WhatsAppTemplateMappingResponse> listMappings() {
        return transaction.listMappings();
    }
}
