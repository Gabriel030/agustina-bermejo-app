package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class SectionInstructorFinder {

    private final SubjectSectionInstructorRepository programInstructorRepository;

    private final CourseSubjectSectionInstructorRepository courseInstructorRepository;

    public SectionInstructorFinder(SubjectSectionInstructorRepository programInstructorRepository,
                                   CourseSubjectSectionInstructorRepository courseInstructorRepository) {
        this.programInstructorRepository = programInstructorRepository;
        this.courseInstructorRepository = courseInstructorRepository;
    }

    public List<SectionInstructorView> findCourseSectionInstructors(Long courseSectionId) {
        return courseInstructorRepository.findBySectionId(courseSectionId).stream()
                .map(link -> viewOf(link.getInstructor(), link.getRole()))
                .toList();
    }

    public List<SectionInstructorView> findProgramSectionInstructors(Long programSectionId) {
        return programInstructorRepository.findBySectionId(programSectionId).stream()
                .map(this::toView)
                .toList();
    }

    public Map<Long, List<SectionInstructorView>> findProgramSectionInstructors(Collection<Long> programSectionIds) {
        Map<Long, List<SectionInstructorView>> instructorsBySectionId = new LinkedHashMap<>();
        programSectionIds.forEach(sectionId -> instructorsBySectionId.put(sectionId, new java.util.ArrayList<>()));
        if (programSectionIds.isEmpty()) {
            return instructorsBySectionId;
        }
        programInstructorRepository.findBySectionIdIn(programSectionIds).forEach(link ->
                instructorsBySectionId.get(link.getSection().getId()).add(toView(link)));
        return instructorsBySectionId;
    }

    public Optional<Long> findProgramSectionLeadPersonId(Long programSectionId) {
        return programInstructorRepository.findBySectionId(programSectionId).stream()
                .filter(link -> link.getRole() == InstructorRole.LEAD)
                .findFirst()
                .map(link -> link.getInstructor().getPerson().getId());
    }

    private SectionInstructorView toView(SubjectSectionInstructor link) {
        return viewOf(link.getInstructor(), link.getRole());
    }

    private static SectionInstructorView viewOf(Instructor instructor, InstructorRole role) {
        var person = instructor.getPerson();
        return new SectionInstructorView(
                instructor.getId(), person.getId(), person.getName() + " " + person.getLastname(), role);
    }
}
