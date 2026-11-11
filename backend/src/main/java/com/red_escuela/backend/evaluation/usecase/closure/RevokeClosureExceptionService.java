package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.specialist.ClosureExceptionManager;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeClosureExceptionService implements RevokeClosureExceptionUseCase {

    private final ClosureExceptionManager closureExceptionManager;

    public RevokeClosureExceptionService(ClosureExceptionManager closureExceptionManager) {
        this.closureExceptionManager = closureExceptionManager;
    }

    @Override
    public void revoke(RevokeClosureExceptionCommand command) {
        closureExceptionManager.revokeException(command.kind(), command.sectionId(), command.enrollmentId());
    }
}
