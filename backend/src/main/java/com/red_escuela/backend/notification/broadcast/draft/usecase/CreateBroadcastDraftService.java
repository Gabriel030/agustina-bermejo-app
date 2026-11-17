package com.red_escuela.backend.notification.broadcast.draft.usecase;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCriterionRequest;
import com.red_escuela.backend.notification.broadcast.audience.exception.SegmentNotAvailableException;
import com.red_escuela.backend.notification.broadcast.audience.specialist.SegmentResolver;
import com.red_escuela.backend.notification.broadcast.draft.dto.BroadcastDraftResponse;
import com.red_escuela.backend.notification.broadcast.draft.dto.CreateBroadcastDraftRequest;
import com.red_escuela.backend.notification.broadcast.draft.service.CreateBroadcastDraftTransaction;
import com.red_escuela.backend.shared.UseCase;

import java.util.Set;
import java.util.stream.Collectors;

@UseCase
public class CreateBroadcastDraftService implements CreateBroadcastDraftUseCase {

    private final SegmentResolver segmentResolver;
    private final CurrentActorFinder currentActorFinder;
    private final ObjectMapper objectMapper;
    private final CreateBroadcastDraftTransaction transaction;

    public CreateBroadcastDraftService(SegmentResolver segmentResolver, CurrentActorFinder currentActorFinder,
                                       ObjectMapper objectMapper, CreateBroadcastDraftTransaction transaction) {
        this.segmentResolver = segmentResolver;
        this.currentActorFinder = currentActorFinder;
        this.objectMapper = objectMapper;
        this.transaction = transaction;
    }

    @Override
    public BroadcastDraftResponse createBroadcastDraft(CreateBroadcastDraftRequest request) {
        Set<?> available = segmentResolver.availableSegments().stream().map(descriptor -> descriptor.key()).collect(Collectors.toSet());
        for (SegmentCriterionRequest criterion : request.segmentSpec().criteria()) {
            if (!available.contains(criterion.key())) {
                throw new SegmentNotAvailableException(criterion.key());
            }
        }
        return transaction.createBroadcastDraft(request, toJson(request), currentActorFinder.currentPersonId());
    }

    private String toJson(CreateBroadcastDraftRequest request) {
        try {
            return objectMapper.writeValueAsString(request.segmentSpec());
        } catch (JacksonException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
