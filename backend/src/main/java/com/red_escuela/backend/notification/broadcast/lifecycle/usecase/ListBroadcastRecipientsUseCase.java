package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.*;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListBroadcastRecipientsUseCase {

    PageResponse<BroadcastRecipientResponse> listBroadcastRecipients(Long broadcastId, BroadcastRecipientStatus status, int page, int size);
}
