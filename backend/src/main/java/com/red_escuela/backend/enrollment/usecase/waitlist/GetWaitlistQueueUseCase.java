package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueResponse;

public interface GetWaitlistQueueUseCase {
    WaitlistQueueResponse get(WaitlistKind kind, Long queueId);
}
