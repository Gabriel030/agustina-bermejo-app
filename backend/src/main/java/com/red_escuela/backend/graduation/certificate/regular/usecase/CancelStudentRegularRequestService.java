package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.specialist.StudentRegularRequestCanceller;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelStudentRegularRequestService implements CancelStudentRegularRequestUseCase {

    private final StudentRegularRequestCanceller canceller;
    private final CurrentStudentService currentStudentService;

    public CancelStudentRegularRequestService(StudentRegularRequestCanceller canceller, CurrentStudentService currentStudentService) {
        this.canceller = canceller;
        this.currentStudentService = currentStudentService;
    }

    @Override
    public void cancel(Long requestId) {
        canceller.cancel(requestId, currentStudentService.resolveCurrentStudent().getId());
    }
}
