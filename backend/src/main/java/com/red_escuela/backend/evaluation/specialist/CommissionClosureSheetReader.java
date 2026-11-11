package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureStudentAssessment;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.exception.ClosureNotAvailableException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommissionClosureSheetReader {

    private final SectionFinder sectionFinder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureSnapshotRecorder snapshotRecorder;
    private final ClosureRequirementsCalculator requirementsCalculator;
    private final ClosureColumnsFinder closureColumnsFinder;
    private final ClosureSheetAssembler sheetAssembler;

    public CommissionClosureSheetReader(SectionFinder sectionFinder, SectionTeachingFinder sectionTeachingFinder,
            CommissionClosureRepository closureRepository, CommissionClosureSnapshotRecorder snapshotRecorder,
            ClosureRequirementsCalculator requirementsCalculator, ClosureColumnsFinder closureColumnsFinder,
            ClosureSheetAssembler sheetAssembler) {
        this.sectionFinder = sectionFinder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.closureRepository = closureRepository;
        this.snapshotRecorder = snapshotRecorder;
        this.requirementsCalculator = requirementsCalculator;
        this.closureColumnsFinder = closureColumnsFinder;
        this.sheetAssembler = sheetAssembler;
    }

    @Transactional
    public ClosureSheetResponse readSheet(ClosureSectionKind sectionKind, Long sectionId, ClosureViewerProfile viewer, Long instructorId) {
        SectionKind kind = sectionKind.toSectionKind();
        sectionFinder.requireExists(kind, sectionId);
        if (viewer == ClosureViewerProfile.TEACHER) {
            sectionTeachingFinder.requireTeaches(kind, sectionId, instructorId);
        }
        CommissionClosure closure = closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .orElseGet(() -> createDraftClosure(kind, sectionId));
        List<ClosureStudentAssessment> assessments = closure.isOpenForTeacher()
                ? snapshotRecorder.recordSnapshot(closure.getId()).assessments()
                : requirementsCalculator.calculate(kind, sectionId);
        return sheetAssembler.assemble(closure, assessments, viewer);
    }

    private CommissionClosure createDraftClosure(SectionKind kind, Long sectionId) {
        if (!closureColumnsFinder.hasAnyPublished(kind, sectionId)) {
            throw new ClosureNotAvailableException(sectionId);
        }
        return closureRepository.save(CommissionClosure.builder()
                .sectionKind(kind)
                .sectionId(sectionId)
                .status(ClosureStatus.DRAFT_AVAILABLE)
                .build());
    }
}
