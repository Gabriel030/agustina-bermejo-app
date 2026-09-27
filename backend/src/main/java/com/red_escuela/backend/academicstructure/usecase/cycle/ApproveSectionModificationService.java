package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ApproveSectionModificationService implements ApproveSectionModificationUseCase {

    private final SubjectSectionRepository sectionRepository;

    public ApproveSectionModificationService(SubjectSectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    @Override
    @Transactional
    public void approveModification(Long sectionId) {
        var section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        section.setPendingApproval(false);
        sectionRepository.save(section);
    }
}
