package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.SectionModificationRequestResponse;
import com.red_escuela.backend.academicstructure.service.SectionModificationRequestLister;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPendingSectionModificationsService implements ListPendingSectionModificationsUseCase {

    private final SectionModificationRequestLister requestLister;

    public ListPendingSectionModificationsService(SectionModificationRequestLister requestLister) {
        this.requestLister = requestLister;
    }

    @Override
    public List<SectionModificationRequestResponse> list() {
        return requestLister.listPendingRequests();
    }
}
