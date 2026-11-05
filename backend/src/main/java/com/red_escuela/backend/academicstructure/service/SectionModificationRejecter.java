package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.SectionModificationStatus;
import com.red_escuela.backend.academicstructure.exception.SectionModificationNotPendingException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class SectionModificationRejecter {

    private final SubjectSectionRepository sectionRepository;
    private final SectionModificationProposalRepository proposalRepository;
    private final Clock clock;

    public SectionModificationRejecter(
            SubjectSectionRepository sectionRepository,
            SectionModificationProposalRepository proposalRepository,
            Clock clock
    ) {
        this.sectionRepository = sectionRepository;
        this.proposalRepository = proposalRepository;
        this.clock = clock;
    }

    @Transactional
    public void rejectModification(Long sectionId, String reason) {
        var section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        var proposal = proposalRepository.findFirstBySubjectSectionIdAndStatus(sectionId, SectionModificationStatus.PENDING)
                .orElseThrow(() -> new SectionModificationNotPendingException(sectionId));

        proposal.reject(reason, LocalDateTime.now(clock));
        proposalRepository.save(proposal);
        section.setPendingApproval(false);
        sectionRepository.save(section);
    }
}
