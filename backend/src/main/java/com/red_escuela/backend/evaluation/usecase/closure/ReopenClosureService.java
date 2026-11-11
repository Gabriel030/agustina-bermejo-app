package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.specialist.CommissionClosureReopener;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReopenClosureService implements ReopenClosureUseCase {

    private final CommissionClosureReopener commissionClosureReopener;

    public ReopenClosureService(CommissionClosureReopener commissionClosureReopener) {
        this.commissionClosureReopener = commissionClosureReopener;
    }

    @Override
    public void reopen(ReopenClosureCommand command) {
        commissionClosureReopener.reopenClosure(command.kind(), command.sectionId(), command.reason());
    }
}
