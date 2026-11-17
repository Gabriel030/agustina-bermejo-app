package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueSummaryResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListWaitlistQueuesUseCase {
    PageResponse<WaitlistQueueSummaryResponse> list(ListWaitlistQueuesCommand command);

    record ListWaitlistQueuesCommand(WaitlistKind kind, Long termId, Integer expiringWithinDays, int page, int size) {
    }
}
