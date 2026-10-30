package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
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

    public SectionInstructorFinder(SubjectSectionInstructorRepository programInstructorRepository) {
        this.programInstructorRepository = programInstructorRepository;
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
        var instructor = link.getInstructor();
        var person = instructor.getPerson();
        return new SectionInstructorView(
                instructor.getId(), person.getId(), person.getName() + " " + person.getLastname(), link.getRole());
    }
}
