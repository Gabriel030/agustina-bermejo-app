package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.specialist.ClosureExceptionManager;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GrantClosureExceptionService implements GrantClosureExceptionUseCase {

    private final ClosureExceptionManager closureExceptionManager;

    public GrantClosureExceptionService(ClosureExceptionManager closureExceptionManager) {
        this.closureExceptionManager = closureExceptionManager;
    }

    @Override
    public void grant(GrantClosureExceptionCommand command) {
        closureExceptionManager.grantException(command.kind(), command.sectionId(), command.enrollmentId(), command.reason());
    }
}
