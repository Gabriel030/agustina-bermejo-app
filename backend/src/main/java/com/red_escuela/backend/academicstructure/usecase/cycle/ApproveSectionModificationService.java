package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.service.SectionModificationApprover;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveSectionModificationService implements ApproveSectionModificationUseCase {

    private final SectionModificationApprover approver;

    public ApproveSectionModificationService(SectionModificationApprover approver) {
        this.approver = approver;
    }

    @Override
    public void approveModification(Long sectionId) {
        approver.approveModification(sectionId);
    }
}
