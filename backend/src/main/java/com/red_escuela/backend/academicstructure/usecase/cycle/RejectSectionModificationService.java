package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.service.SectionModificationRejecter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectSectionModificationService implements RejectSectionModificationUseCase {

    private final SectionModificationRejecter rejecter;

    public RejectSectionModificationService(SectionModificationRejecter rejecter) {
        this.rejecter = rejecter;
    }

    @Override
    public void rejectModification(Long sectionId, String reason) {
        rejecter.rejectModification(sectionId, reason);
    }
}
