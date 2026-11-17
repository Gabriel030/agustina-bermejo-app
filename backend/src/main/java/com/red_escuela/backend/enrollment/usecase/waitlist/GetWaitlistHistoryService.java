package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.WaitlistHistoryEntryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.service.WaitlistHistoryReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class GetWaitlistHistoryService implements GetWaitlistHistoryUseCase {

    private final WaitlistHistoryReader waitlistHistoryReader;

    public GetWaitlistHistoryService(WaitlistHistoryReader waitlistHistoryReader) {
        this.waitlistHistoryReader = waitlistHistoryReader;
    }

    @Override
    public PageResponse<WaitlistHistoryEntryResponse> get(WaitlistKind kind, Long queueId, int page, int size) {
        return waitlistHistoryReader.read(kind, queueId, page, size);
    }
}
