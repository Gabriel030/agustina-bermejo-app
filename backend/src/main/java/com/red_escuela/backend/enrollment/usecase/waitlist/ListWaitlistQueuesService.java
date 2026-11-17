package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistQueueSummaryResponse;
import com.red_escuela.backend.enrollment.service.WaitlistQueueLister;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListWaitlistQueuesService implements ListWaitlistQueuesUseCase {

    private final WaitlistQueueLister waitlistQueueLister;

    public ListWaitlistQueuesService(WaitlistQueueLister waitlistQueueLister) {
        this.waitlistQueueLister = waitlistQueueLister;
    }

    @Override
    public PageResponse<WaitlistQueueSummaryResponse> list(ListWaitlistQueuesCommand command) {
        return waitlistQueueLister.list(command.kind(), command.termId(), command.expiringWithinDays(), command.page(), command.size());
    }
}
