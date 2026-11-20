package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;

import java.util.List;

public interface ListMessageTemplatesUseCase {
    List<MessageTemplateResponse> listMessageTemplates(ChannelType channel, BroadcastCategory category);
}
