package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;
import com.red_escuela.backend.notification.broadcast.template.repository.MessageTemplateRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMessageTemplatesService implements ListMessageTemplatesUseCase {

    private final MessageTemplateRepository repository;

    public ListMessageTemplatesService(MessageTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MessageTemplateResponse> listMessageTemplates(ChannelType channel, BroadcastCategory category) {
        return repository.findActiveByFilters(channel, category).stream().map(MessageTemplateResponse::from).toList();
    }
}
