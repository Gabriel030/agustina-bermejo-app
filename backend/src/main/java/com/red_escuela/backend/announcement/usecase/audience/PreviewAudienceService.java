package com.red_escuela.backend.announcement.usecase.audience;

import com.red_escuela.backend.announcement.dto.AudiencePreviewResponse;
import com.red_escuela.backend.announcement.dto.AudienceStudentResponse;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.specialist.SectionMembershipChecker;
import com.red_escuela.backend.announcement.specialist.audience.AudienceLabels;
import com.red_escuela.backend.announcement.specialist.audience.AudienceSegmentResolver;
import com.red_escuela.backend.announcement.specialist.audience.SectionSnapshot;
import com.red_escuela.backend.announcement.specialist.audience.StudentSnapshot;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@UseCase
public class PreviewAudienceService implements PreviewAudienceUseCase {

    private static final Logger log = LoggerFactory.getLogger(PreviewAudienceService.class);

    private final AudienceSegmentResolver audienceService;
    private final SectionMembershipChecker sectionMembership;

    public PreviewAudienceService(AudienceSegmentResolver audienceService,
                                  SectionMembershipChecker sectionMembership) {
        this.audienceService = audienceService;
        this.sectionMembership = sectionMembership;
    }

    @Override
    public AudiencePreviewResponse preview(PreviewAudienceCommand command) {
        sectionMembership.requireCurrentInstructorOf(command.sectionType(), command.sectionId());

        SectionSnapshot section = audienceService.snapshotOf(command.sectionType(), command.sectionId());
        List<StudentSnapshot> matched = audienceService.resolve(section, command.spec());
        boolean ungraded = hasUngradedInstances(section, command.spec().getKey());

        if (ungraded) {
            log.warn("Segmento sobre instancias sin corregir: comision={}/{}, segmento={}",
                    command.sectionType(), command.sectionId(), command.spec().getKey());
        }

        return new AudiencePreviewResponse(
                matched.size(),
                section.students().size(),
                AudienceLabels.resolvedLabel(command.spec()),
                ungraded,
                matched.stream().map(AudienceStudentResponse::from).toList());
    }

    private boolean hasUngradedInstances(SectionSnapshot section, AudienceSegmentKey key) {
        return switch (key) {
            case EXAM_FAILED, EXAM_PASSED, EXAM_MISSING ->
                    someoneIsMissingScores(section, section.examCount(), StudentSnapshot::examScores);
            case TP_FAILED, TP_MISSING, TP_PASSED ->
                    someoneIsMissingScores(section, section.tpCount(), StudentSnapshot::tpScores);
            default -> false;
        };
    }

    private boolean someoneIsMissingScores(SectionSnapshot section,
                                          int expected,
                                          Function<StudentSnapshot, Map<Integer, Double>> scores) {
        if (expected == 0) {
            return false;
        }
        return section.students().stream().anyMatch(student -> scores.apply(student).size() < expected);
    }
}
