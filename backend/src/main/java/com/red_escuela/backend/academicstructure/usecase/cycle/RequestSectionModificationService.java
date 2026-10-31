package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.service.SectionModificationRequester;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestSectionModificationService implements RequestSectionModificationUseCase {

    private final SectionModificationRequester requester;

    public RequestSectionModificationService(SectionModificationRequester requester) {
        this.requester = requester;
    }

    @Override
    public void requestModification(Long sectionId, ModifySectionRequest request) {
        requester.requestModification(sectionId, request);
    }
}
