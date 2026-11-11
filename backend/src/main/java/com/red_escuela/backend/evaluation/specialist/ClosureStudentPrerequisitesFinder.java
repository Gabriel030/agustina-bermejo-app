package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.PrerequisiteBatchQuery;
import com.red_escuela.backend.enrollment.api.PrerequisiteBatchResult;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteBatchEvaluator;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.domain.ClosurePrerequisiteStatus;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.dto.ClosurePrerequisiteResponse;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.exception.ClosureStudentNotFoundException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClosureStudentPrerequisitesFinder {

    private final CommissionClosureRepository closureRepository;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final ProgramPrerequisiteBatchEvaluator prerequisiteBatchEvaluator;

    public ClosureStudentPrerequisitesFinder(CommissionClosureRepository closureRepository,
            SectionTeachingFinder sectionTeachingFinder, SectionEnrollmentFinder sectionEnrollmentFinder,
            ProgramPrerequisiteBatchEvaluator prerequisiteBatchEvaluator) {
        this.closureRepository = closureRepository;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.prerequisiteBatchEvaluator = prerequisiteBatchEvaluator;
    }

    @Transactional(readOnly = true)
    public List<ClosurePrerequisiteResponse> findPrerequisites(Long closureId, Long enrollmentId,
            ClosureViewerProfile viewer, Long instructorId) {
        CommissionClosure closure = closureRepository.findById(closureId)
                .orElseThrow(() -> new ClosureStudentNotFoundException(enrollmentId));
        if (viewer == ClosureViewerProfile.TEACHER) {
            sectionTeachingFinder.requireTeaches(closure.getSectionKind(), closure.getSectionId(), instructorId);
        }
        if (closure.getSectionKind() == SectionKind.COURSE) {
            return List.of();
        }
        SubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findProgramEnrollment(enrollmentId);
        if (!closure.getSectionId().equals(enrollment.subjectSectionId())) {
            throw new ClosureStudentNotFoundException(enrollmentId);
        }
        PrerequisiteBatchResult result = prerequisiteBatchEvaluator.assessAll(List.of(new PrerequisiteBatchQuery(
                enrollmentId, enrollment.getProgramEnrollment().getId(), enrollment.subjectId()))).get(enrollmentId);
        if (result == null) {
            return List.of();
        }
        return result.details().stream()
                .map(detail -> new ClosurePrerequisiteResponse(detail.subjectCode(), detail.subjectName(),
                        detail.requirementType(),
                        ClosurePrerequisiteStatus.valueOf(String.valueOf(detail.standing())), detail.metForApproval()))
                .toList();
    }
}
