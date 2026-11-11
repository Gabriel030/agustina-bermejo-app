package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClosureSectionEnrollmentLocker {

    private final SectionEnrollmentFinder sectionEnrollmentFinder;

    public ClosureSectionEnrollmentLocker(SectionEnrollmentFinder sectionEnrollmentFinder) {
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockMembersInAscendingOrder(SectionKind kind, Long sectionId) {
        if (kind == SectionKind.PROGRAM) {
            memberIdsOfProgramSection(sectionId).forEach(sectionEnrollmentFinder::lockProgramEnrollment);
        } else {
            memberIdsOfCourseSection(sectionId).forEach(sectionEnrollmentFinder::lockCourseEnrollment);
        }
    }

    public List<Long> memberIds(SectionKind kind, Long sectionId) {
        return kind == SectionKind.PROGRAM ? memberIdsOfProgramSection(sectionId) : memberIdsOfCourseSection(sectionId);
    }

    private List<Long> memberIdsOfProgramSection(Long sectionId) {
        return sectionEnrollmentFinder.findMembersOfProgramSection(sectionId).stream()
                .map(SubjectSectionEnrollment::getId).sorted().toList();
    }

    private List<Long> memberIdsOfCourseSection(Long sectionId) {
        return sectionEnrollmentFinder.findMembersOfCourseSection(sectionId).stream()
                .map(CourseSubjectSectionEnrollment::getId).sorted().toList();
    }
}
