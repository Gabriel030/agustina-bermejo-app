package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.SectionModificationConflictResponse;
import com.red_escuela.backend.academicstructure.dto.SectionModificationPreviewResponse;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SectionModificationPreviewer {

    private final SubjectSectionRepository sectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionModificationConflictDetector conflictDetector;
    private final SectionCycleApprovalGuard cycleApprovalGuard;

    public SectionModificationPreviewer(
            SubjectSectionRepository sectionRepository,
            SectionScheduleSlotRepository slotRepository,
            SectionModificationConflictDetector conflictDetector,
            SectionCycleApprovalGuard cycleApprovalGuard
    ) {
        this.sectionRepository = sectionRepository;
        this.slotRepository = slotRepository;
        this.conflictDetector = conflictDetector;
        this.cycleApprovalGuard = cycleApprovalGuard;
    }

    @Transactional(readOnly = true)
    public SectionModificationPreviewResponse previewModification(Long sectionId, ModifySectionRequest request) {
        var section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        cycleApprovalGuard.ensureCycleIsApproved(section);
        if (request.slots() == null || request.slots().isEmpty()) {
            throw new RequestFieldRequiredException("Indicá al menos una franja horaria para previsualizar el cambio");
        }
        Set<Long> currentSlotIds = slotRepository.findBySubjectSectionId(sectionId).stream()
                .map(SectionScheduleSlot::getId)
                .collect(Collectors.toSet());
        var conflicts = conflictDetector.detectAll(section, request.slots(), currentSlotIds).stream()
                .map(conflict -> new SectionModificationConflictResponse(
                        conflict.slotIndex(), conflict.type(), conflict.conflictingSectionLabel()))
                .toList();
        return new SectionModificationPreviewResponse(conflicts);
    }
}
