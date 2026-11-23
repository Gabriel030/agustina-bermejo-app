package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.SectionModificationRequestResponse;

import java.util.List;

public interface ListPendingSectionModificationsUseCase {
    List<SectionModificationRequestResponse> list();
}
