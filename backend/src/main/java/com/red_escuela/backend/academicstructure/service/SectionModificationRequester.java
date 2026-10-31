package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.entity.ProposedSlot;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionModificationAlreadyPendingException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SectionModificationRequester {

    private final SubjectSectionRepository sectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionModificationProposalRepository proposalRepository;
    private final SectionConflictChecker conflictChecker;
    private final Clock clock;

    public SectionModificationRequester(
            SubjectSectionRepository sectionRepository,
            SectionScheduleSlotRepository slotRepository,
            ClassroomRepository classroomRepository,
            SectionModificationProposalRepository proposalRepository,
            SectionConflictChecker conflictChecker,
            Clock clock
    ) {
        this.sectionRepository = sectionRepository;
        this.slotRepository = slotRepository;
        this.classroomRepository = classroomRepository;
        this.proposalRepository = proposalRepository;
        this.conflictChecker = conflictChecker;
        this.clock = clock;
    }

    @Transactional
    public void requestModification(Long sectionId, ModifySectionRequest request) {
        var section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        if (section.isPendingApproval()) {
            throw new SectionModificationAlreadyPendingException(sectionId);
        }

        var proposedSlots = new ArrayList<ProposedSlot>();
        if (request.slots() != null) {
            Set<Long> currentSlotIds = slotRepository.findBySubjectSectionId(sectionId).stream()
                    .map(SectionScheduleSlot::getId)
                    .collect(Collectors.toSet());
            for (var slotReq : request.slots()) {
                var classroom = classroomRepository.findById(slotReq.classroomId())
                        .orElseThrow(() -> new ClassroomNotFoundException(slotReq.classroomId()));
                classroom.ensureCanHost(section.getCapacity());
                conflictChecker.checkClassroomAvailabilityIgnoringSlots(
                        section.getTerm(), slotReq.classroomId(), slotReq.dayOfWeek(), slotReq.startTime(), slotReq.endTime(), currentSlotIds);
                var proposed = new ProposedSlot(classroom, slotReq.dayOfWeek(), slotReq.startTime(), slotReq.endTime());
                ensureNoOverlapWith(proposedSlots, proposed);
                proposedSlots.add(proposed);
            }
        }

        proposalRepository.save(SectionModificationProposal.pending(
                section, request.slots() != null, proposedSlots, LocalDateTime.now(clock)));
        section.setPendingApproval(true);
        sectionRepository.save(section);
    }

    private void ensureNoOverlapWith(List<ProposedSlot> accepted, ProposedSlot candidate) {
        boolean overlaps = accepted.stream().anyMatch(other ->
                other.getClassroom().getId().equals(candidate.getClassroom().getId())
                        && other.getDayOfWeek() == candidate.getDayOfWeek()
                        && other.getStartTime().isBefore(candidate.getEndTime())
                        && other.getEndTime().isAfter(candidate.getStartTime()));
        if (overlaps) {
            throw new SectionScheduleConflictException(
                    candidate.getClassroom().getId(), candidate.getDayOfWeek(), candidate.getStartTime(), candidate.getEndTime());
        }
    }
}
