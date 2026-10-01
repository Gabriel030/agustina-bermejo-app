package com.red_escuela.backend.announcement.usecase.audience;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.dto.AudienceSegmentResponse;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.announcement.service.audience.AudienceLabels;
import com.red_escuela.backend.announcement.service.audience.AudienceService;
import com.red_escuela.backend.announcement.service.audience.SectionSnapshot;
import com.red_escuela.backend.service.CurrentInstructorService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListAudienceSegmentsService implements ListAudienceSegmentsUseCase {

    private final AudienceService audienceService;
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;

    public ListAudienceSegmentsService(AudienceService audienceService,
                                       SectionMembership sectionMembership,
                                       CurrentInstructorService currentInstructorService) {
        this.audienceService = audienceService;
        this.sectionMembership = sectionMembership;
        this.currentInstructorService = currentInstructorService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AudienceSegmentResponse> list(SectionType sectionType, Long sectionId) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(sectionType, sectionId, instructor.getId());

        SectionSnapshot section = audienceService.snapshotOf(sectionType, sectionId);
        return audienceService.availableKeys(section).stream()
                .map(key -> toResponse(section, key))
                .toList();
    }

    private AudienceSegmentResponse toResponse(SectionSnapshot section, AudienceSegmentKey key) {
        boolean needsInstances = AudienceLabels.needsInstances(key);
        boolean needsThreshold = AudienceLabels.needsThreshold(key);
        int count = audienceService.countFor(section, key);
        return new AudienceSegmentResponse(
                key,
                AudienceLabels.optionLabel(key),
                count < 0 ? null : count,
                needsInstances,
                needsThreshold,
                needsInstances ? instanceCount(section, key) : null);
    }

    private Integer instanceCount(SectionSnapshot section, AudienceSegmentKey key) {
        return switch (key) {
            case TP_FAILED, TP_MISSING, TP_PASSED -> section.tpCount();
            case FINAL_PASSED, FINAL_FAILED -> 2;
            default -> section.examCount();
        };
    }
}
