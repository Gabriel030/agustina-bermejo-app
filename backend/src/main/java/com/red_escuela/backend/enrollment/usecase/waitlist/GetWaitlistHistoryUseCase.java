package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistHistoryEntryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface GetWaitlistHistoryUseCase {
    PageResponse<WaitlistHistoryEntryResponse> get(WaitlistKind kind, Long queueId, int page, int size);
}
