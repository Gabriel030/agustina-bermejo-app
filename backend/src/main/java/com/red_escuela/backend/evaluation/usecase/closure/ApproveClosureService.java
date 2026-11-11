package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.specialist.CommissionClosureApprover;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveClosureService implements ApproveClosureUseCase {

    private final CommissionClosureApprover commissionClosureApprover;

    public ApproveClosureService(CommissionClosureApprover commissionClosureApprover) {
        this.commissionClosureApprover = commissionClosureApprover;
    }

    @Override
    public void approve(ApproveClosureCommand command) {
        commissionClosureApprover.approveClosure(command.kind(), command.sectionId(), command.book(), command.folio(), command.closeWithoutTeacherConfirmation());
    }
}
