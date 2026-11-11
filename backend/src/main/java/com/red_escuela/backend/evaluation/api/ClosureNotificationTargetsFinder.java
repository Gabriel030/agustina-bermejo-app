package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionInstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionInstructorView;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.evaluation.exception.ClosureNotFoundException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class ClosureNotificationTargetsFinder {

    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureStudentRepository closureStudentRepository;
    private final SectionFinder sectionFinder;
    private final SectionInstructorFinder sectionInstructorFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;

    public ClosureNotificationTargetsFinder(CommissionClosureRepository closureRepository,
            CommissionClosureStudentRepository closureStudentRepository, SectionFinder sectionFinder,
            SectionInstructorFinder sectionInstructorFinder, SectionEnrollmentFinder sectionEnrollmentFinder) {
        this.closureRepository = closureRepository;
        this.closureStudentRepository = closureStudentRepository;
        this.sectionFinder = sectionFinder;
        this.sectionInstructorFinder = sectionInstructorFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
    }

    public ClosureNotificationTargets findClosureTargets(Long closureId) {
        CommissionClosure closure = closureRepository.findById(closureId).orElseThrow(() -> new ClosureNotFoundException(closureId));
        SectionKind kind = closure.getSectionKind();
        Map<Long, Long> personByEnrollment = studentPersonsOf(kind, closure.getSectionId());
        List<ClosureStudentNotificationTarget> students = new ArrayList<>();
        int promoted = 0;
        int goingToFinal = 0;
        for (CommissionClosureStudent row : closureStudentRepository.findByClosureId(closureId)) {
            Long personId = personByEnrollment.get(row.getEnrollmentId());
            if (personId == null || row.getFinalCondition() == null) {
                continue;
            }
            ClosureFinalCondition condition = row.getFinalCondition();
            students.add(new ClosureStudentNotificationTarget(personId, resultLabelOf(condition)));
            promoted += condition == ClosureFinalCondition.PROMOTES ? 1 : 0;
            goingToFinal += isGoingToFinal(condition) ? 1 : 0;
        }
        return new ClosureNotificationTargets(findSectionLabel(kind, closure.getSectionId()), closure.getReportNumber(),
                closure.getReportVersion(), findInstructorPersonIds(kind, closure.getSectionId()), students, promoted, goingToFinal);
    }

    public String findSectionLabel(SectionKind kind, Long sectionId) {
        if (kind == SectionKind.PROGRAM) {
            var section = sectionFinder.findProgramSectionEntity(sectionId);
            return section.getSubject().getName() + " · " + section.getName();
        }
        var section = sectionFinder.findCourseSectionEntity(sectionId);
        return section.getCourseSubject().getName() + " · " + section.getName();
    }

    public List<Long> findInstructorPersonIds(SectionKind kind, Long sectionId) {
        return (kind == SectionKind.PROGRAM
                ? sectionInstructorFinder.findProgramSectionInstructors(sectionId)
                : sectionInstructorFinder.findCourseSectionInstructors(sectionId)).stream()
                .map(SectionInstructorView::personId)
                .distinct()
                .toList();
    }

    private Map<Long, Long> studentPersonsOf(SectionKind kind, Long sectionId) {
        Map<Long, Long> persons = new HashMap<>();
        if (kind == SectionKind.PROGRAM) {
            sectionEnrollmentFinder.findMembersWithStudentInProgramSection(sectionId)
                    .forEach(enrollment -> persons.put(enrollment.getId(), enrollment.getStudent().getPerson().getId()));
        } else {
            sectionEnrollmentFinder.findMembersWithStudentInCourseSection(sectionId)
                    .forEach(enrollment -> persons.put(enrollment.getId(), enrollment.getStudent().getPerson().getId()));
        }
        return persons;
    }

    private boolean isGoingToFinal(ClosureFinalCondition condition) {
        return condition == ClosureFinalCondition.GOES_TO_FINAL || condition == ClosureFinalCondition.PROMOTION_CONDITIONAL;
    }

    private String resultLabelOf(ClosureFinalCondition condition) {
        return switch (condition) {
            case PROMOTES -> "Promociona";
            case PROMOTION_CONDITIONAL, GOES_TO_FINAL -> "Va a final";
            case FAILED -> "Desaprobado";
            case FREE -> "Libre";
        };
    }
}
