package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.entity.ProposedSlot;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionModificationAlreadyPendingException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.events.SectionModificationRequestedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SectionModificationRequester {

    private final SubjectSectionRepository sectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionModificationProposalRepository proposalRepository;
    private final SectionModificationConflictDetector conflictDetector;
    private final SectionCycleApprovalGuard cycleApprovalGuard;
    private final CurrentActorFinder currentActorFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SectionModificationRequester(
            SubjectSectionRepository sectionRepository,
            SectionScheduleSlotRepository slotRepository,
            ClassroomRepository classroomRepository,
            SectionModificationProposalRepository proposalRepository,
            SectionModificationConflictDetector conflictDetector,
            SectionCycleApprovalGuard cycleApprovalGuard,
            CurrentActorFinder currentActorFinder,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.sectionRepository = sectionRepository;
        this.slotRepository = slotRepository;
        this.classroomRepository = classroomRepository;
        this.proposalRepository = proposalRepository;
        this.conflictDetector = conflictDetector;
        this.cycleApprovalGuard = cycleApprovalGuard;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void requestModification(Long sectionId, ModifySectionRequest request) {
        var section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        cycleApprovalGuard.ensureCycleIsApproved(section);
        if (section.isPendingApproval()) {
            throw new SectionModificationAlreadyPendingException(sectionId);
        }

        var proposedSlots = new ArrayList<ProposedSlot>();
        if (request.slots() != null) {
            Set<Long> currentSlotIds = slotRepository.findBySubjectSectionId(sectionId).stream()
                    .map(SectionScheduleSlot::getId)
                    .collect(Collectors.toSet());
            for (int index = 0; index < request.slots().size(); index++) {
                conflictDetector.detectForSlot(section, request.slots(), index, currentSlotIds).stream()
                        .findFirst()
                        .ifPresent(conflict -> {
                            throw conflict.exception().get();
                        });
                var slotReq = request.slots().get(index);
                var classroom = classroomRepository.findById(slotReq.classroomId())
                        .orElseThrow(() -> new ClassroomNotFoundException(slotReq.classroomId()));
                proposedSlots.add(new ProposedSlot(classroom, slotReq.dayOfWeek(), slotReq.startTime(), slotReq.endTime()));
            }
        }

        var proposal = SectionModificationProposal.pending(
                section, request.slots() != null, proposedSlots, currentActorFinder.currentPerson(), LocalDateTime.now(clock));
        proposalRepository.save(proposal);
        section.setPendingApproval(true);
        sectionRepository.save(section);
        eventPublisher.publishEvent(SectionModificationRequestedEvent.requestedFor(proposal));
    }
}
