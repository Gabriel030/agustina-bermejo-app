package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse.SlotInfo;
import com.red_escuela.backend.academicstructure.dto.SectionModificationRequestResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.ProposedSlot;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SectionModificationRequestLister {

    private static final String UNKNOWN_REQUESTER = "—";

    private static final Comparator<SlotInfo> SLOT_ORDER = Comparator
            .comparing((SlotInfo slot) -> DayOfWeek.valueOf(slot.dayOfWeek()))
            .thenComparing(SlotInfo::startTime)
            .thenComparing(SlotInfo::classroomId);

    private final SectionModificationProposalRepository proposalRepository;
    private final SectionScheduleSlotRepository slotRepository;

    public SectionModificationRequestLister(
            SectionModificationProposalRepository proposalRepository,
            SectionScheduleSlotRepository slotRepository
    ) {
        this.proposalRepository = proposalRepository;
        this.slotRepository = slotRepository;
    }

    @Transactional(readOnly = true)
    public List<SectionModificationRequestResponse> listPendingRequests() {
        var proposals = proposalRepository.findPendingOfApprovedCyclesOrderByRequestedAt();
        if (proposals.isEmpty()) {
            return List.of();
        }
        var sectionIds = proposals.stream().map(p -> p.getSubjectSection().getId()).toList();
        var currentSlotsBySection = slotRepository.findWithClassroomBySubjectSectionIdIn(sectionIds).stream()
                .collect(Collectors.groupingBy(
                        slot -> slot.getSubjectSection().getId(),
                        Collectors.mapping(this::toSlotInfo, Collectors.toList())));
        return proposals.stream()
                .map(proposal -> toResponse(proposal, currentSlotsBySection))
                .toList();
    }

    private SectionModificationRequestResponse toResponse(
            SectionModificationProposal proposal, Map<Long, List<SlotInfo>> currentSlotsBySection) {
        var section = proposal.getSubjectSection();
        var cycle = section.getAcademicOfferingCycle();
        return new SectionModificationRequestResponse(
                section.getId(),
                section.getName(),
                section.getSubject().getName(),
                offeringNameOf(cycle),
                cycle.getId(),
                requesterNameOf(proposal),
                proposal.getRequestedAt().toString(),
                proposal.isReplacesSchedule(),
                currentSlotsBySection.getOrDefault(section.getId(), List.of()).stream().sorted(SLOT_ORDER).toList(),
                proposal.getSlots().stream().map(this::toSlotInfo).sorted(SLOT_ORDER).toList());
    }

    private String offeringNameOf(AcademicOfferingCycle cycle) {
        return cycle.getProgram() != null ? cycle.getProgram().getName() : cycle.getCourse().getName();
    }

    private String requesterNameOf(SectionModificationProposal proposal) {
        var requester = proposal.getRequestedBy();
        return requester == null ? UNKNOWN_REQUESTER : requester.getName() + " " + requester.getLastname();
    }

    private SlotInfo toSlotInfo(SectionScheduleSlot slot) {
        return new SlotInfo(
                slot.getDayOfWeek().name(),
                slot.getStartTime().toString(),
                slot.getEndTime().toString(),
                slot.getClassroom().getId(),
                slot.getClassroom().getName());
    }

    private SlotInfo toSlotInfo(ProposedSlot slot) {
        return new SlotInfo(
                slot.getDayOfWeek().name(),
                slot.getStartTime().toString(),
                slot.getEndTime().toString(),
                slot.getClassroom().getId(),
                slot.getClassroom().getName());
    }
}
