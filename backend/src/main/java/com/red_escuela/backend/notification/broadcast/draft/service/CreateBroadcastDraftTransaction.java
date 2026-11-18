package com.red_escuela.backend.notification.broadcast.draft.service;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.dto.BroadcastDraftResponse;
import com.red_escuela.backend.notification.broadcast.draft.dto.CreateBroadcastDraftRequest;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastBodyRequiredException;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastContentWithTemplateException;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastTemplateMismatchException;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.template.entity.MessageTemplate;
import com.red_escuela.backend.notification.broadcast.template.exception.MessageTemplateNotFoundException;
import com.red_escuela.backend.notification.broadcast.template.exception.TemplateSubjectRequiredException;
import com.red_escuela.backend.notification.broadcast.template.repository.MessageTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class CreateBroadcastDraftTransaction {

    private final BroadcastRepository broadcastRepository;
    private final MessageTemplateRepository templateRepository;
    private final Clock clock;

    public CreateBroadcastDraftTransaction(BroadcastRepository broadcastRepository,
                                           MessageTemplateRepository templateRepository, Clock clock) {
        this.broadcastRepository = broadcastRepository;
        this.templateRepository = templateRepository;
        this.clock = clock;
    }

    @Transactional
    public BroadcastDraftResponse createBroadcastDraft(CreateBroadcastDraftRequest request, String segmentSpecJson,
                                                       Long createdByPersonId) {
        String subject = request.subject();
        String body = request.bodyText();
        if (request.templateId() != null) {
            if (request.subject() != null || request.bodyText() != null) {
                throw new BroadcastContentWithTemplateException();
            }
            MessageTemplate template = templateRepository.findByIdAndActiveTrue(request.templateId())
                    .orElseThrow(() -> new MessageTemplateNotFoundException(request.templateId()));
            if (template.getChannel() != request.channel() || template.getCategory() != request.category()) {
                throw new BroadcastTemplateMismatchException();
            }
            subject = template.getSubject();
            body = template.getBodyText();
        }
        if (body == null || body.isBlank()) {
            throw new BroadcastBodyRequiredException();
        }
        if (request.channel() == ChannelType.EMAIL && (subject == null || subject.isBlank())) {
            throw new TemplateSubjectRequiredException();
        }
        Broadcast broadcast = Broadcast.builder()
                .name(request.name().strip())
                .category(request.category())
                .channel(request.channel())
                .status(BroadcastStatus.DRAFT)
                .segmentSpec(segmentSpecJson)
                .subjectSnapshot(subject)
                .bodySnapshot(body)
                .templateId(request.templateId())
                .createdByPersonId(createdByPersonId)
                .createdAt(clock.instant())
                .build();
        Broadcast saved = broadcastRepository.save(broadcast);
        return new BroadcastDraftResponse(saved.getId(), saved.getStatus());
    }
}
