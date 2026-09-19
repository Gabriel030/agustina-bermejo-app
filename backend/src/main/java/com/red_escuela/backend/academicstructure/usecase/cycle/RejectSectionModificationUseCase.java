package com.red_escuela.backend.academicstructure.usecase.cycle;

public interface RejectSectionModificationUseCase {
    void rejectModification(Long sectionId, String reason);
}
