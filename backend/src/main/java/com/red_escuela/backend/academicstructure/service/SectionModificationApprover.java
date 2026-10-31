package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionModificationStatus;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.SectionModificationNotPendingException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SectionModificationApprover {

    private final SubjectSectionRepository sectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionModificationProposalRepository proposalRepository;
    private final SectionConflictChecker conflictChecker;
    private final Clock clock;

    public SectionModificationApprover(
            SubjectSectionRepository sectionRepository,
            SectionScheduleSlotRepository slotRepository,
            SectionModificationProposalRepository proposalRepository,
            SectionConflictChecker conflictChecker,
            Clock clock
    ) {
        this.sectionRepository = sectionRepository;
        this.slotRepository = slotRepository;
        this.proposalRepository = proposalRepository;
        this.conflictChecker = conflictChecker;
        this.clock = clock;
    }

    @Transactional
    public void approveModification(Long sectionId) {
        var section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        var proposal = proposalRepository.findFirstBySubjectSectionIdAndStatus(sectionId, SectionModificationStatus.PENDING)
                .orElseThrow(() -> new SectionModificationNotPendingException(sectionId));

        if (proposal.isReplacesSchedule()) {
            replaceSchedule(section, proposal);
        }

        proposal.approve(LocalDateTime.now(clock));
        proposalRepository.save(proposal);
        section.setPendingApproval(false);
        sectionRepository.save(section);
    }

    private void replaceSchedule(SubjectSection section, SectionModificationProposal proposal) {
        var current = slotRepository.findBySubjectSectionId(section.getId());
        Set<Long> currentIds = current.stream().map(SectionScheduleSlot::getId).collect(Collectors.toSet());
        for (var proposed : proposal.getSlots()) {
            proposed.getClassroom().ensureCanHost(section.getCapacity());
            conflictChecker.checkClassroomAvailabilityIgnoringSlots(
                    section.getTerm(), proposed.getClassroom().getId(), proposed.getDayOfWeek(),
                    proposed.getStartTime(), proposed.getEndTime(), currentIds);
        }
        slotRepository.deleteAll(current);
        slotRepository.flush();
        for (var proposed : proposal.getSlots()) {
            var slot = new SectionScheduleSlot();
            slot.setSubjectSection(section);
            slot.setClassroom(proposed.getClassroom());
            slot.setDayOfWeek(proposed.getDayOfWeek());
            slot.setStartTime(proposed.getStartTime());
            slot.setEndTime(proposed.getEndTime());
            slotRepository.save(slot);
        }
    }
}
