package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.academicstructure.api.CurrentInstructorIdFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SectionMembershipChecker {

    private static final String DIRECTOR_AUTHORITY = "ROLE_DIRECTOR";

    private final SectionTeachingFinder sectionTeachingFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final CurrentInstructorIdFinder currentInstructorIdFinder;

    public SectionMembershipChecker(SectionTeachingFinder sectionTeachingFinder,
                             SectionEnrollmentFinder sectionEnrollmentFinder,
                             CurrentInstructorIdFinder currentInstructorIdFinder) {
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.currentInstructorIdFinder = currentInstructorIdFinder;
    }

    public void requireCurrentInstructorOf(SectionType type, Long sectionId) {
        if (currentActorIsDirector()) {
            return;
        }
        requireInstructorOf(type, sectionId, currentInstructorIdFinder.currentInstructorId());
    }

    private boolean currentActorIsDirector() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> DIRECTOR_AUTHORITY.equals(authority.getAuthority()));
    }

    public void requireInstructorOf(SectionType type, Long sectionId, Long instructorId) {
        SectionKind kind = type == SectionType.PROGRAM ? SectionKind.PROGRAM : SectionKind.COURSE;
        if (!sectionTeachingFinder.teaches(kind, sectionId, instructorId)) {
            throw new NotSectionInstructorException(sectionId);
        }
    }

    public List<Student> studentsOf(SectionType type, Long sectionId) {
        if (type == SectionType.PROGRAM) {
            return sectionEnrollmentFinder
                    .findMembersOfProgramSection(sectionId)
                    .stream()
                    .map(e -> e.getStudent())
                    .toList();
        }
        return sectionEnrollmentFinder
                .findMembersOfCourseSection(sectionId)
                .stream()
                .map(e -> e.getStudent())
                .toList();
    }
}
