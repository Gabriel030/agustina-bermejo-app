package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueResponse;
import com.red_escuela.backend.enrollment.service.WaitlistQueueReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetWaitlistQueueService implements GetWaitlistQueueUseCase {

    private final WaitlistQueueReader waitlistQueueReader;

    public GetWaitlistQueueService(WaitlistQueueReader waitlistQueueReader) {
        this.waitlistQueueReader = waitlistQueueReader;
    }

    @Override
    public WaitlistQueueResponse get(WaitlistKind kind, Long queueId) {
        return waitlistQueueReader.read(kind, queueId);
    }
}
