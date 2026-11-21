package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.SectionModificationRequestResponse;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPendingSectionModificationsService implements ListPendingSectionModificationsUseCase {

    @Override
    public List<SectionModificationRequestResponse> list() {
        return List.of();
    }
}
