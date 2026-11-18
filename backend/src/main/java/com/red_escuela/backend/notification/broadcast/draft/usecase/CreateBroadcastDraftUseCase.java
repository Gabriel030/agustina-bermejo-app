package com.red_escuela.backend.notification.broadcast.draft.usecase;

import com.red_escuela.backend.notification.broadcast.draft.dto.BroadcastDraftResponse;
import com.red_escuela.backend.notification.broadcast.draft.dto.CreateBroadcastDraftRequest;

public interface CreateBroadcastDraftUseCase {

    BroadcastDraftResponse createBroadcastDraft(CreateBroadcastDraftRequest request);
}
