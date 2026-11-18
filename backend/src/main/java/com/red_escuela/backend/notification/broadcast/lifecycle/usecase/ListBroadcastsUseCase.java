package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.*;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListBroadcastsUseCase {

    PageResponse<BroadcastSummaryResponse> listBroadcasts(BroadcastStatus status, int page, int size);
}
