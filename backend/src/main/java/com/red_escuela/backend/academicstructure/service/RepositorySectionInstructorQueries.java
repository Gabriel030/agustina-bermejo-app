package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.SectionInstructorView;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.usecase.SectionInstructorQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RepositorySectionInstructorQueries implements SectionInstructorQueries {

    private final SubjectSectionInstructorRepository programInstructorRepository;

    public RepositorySectionInstructorQueries(SubjectSectionInstructorRepository programInstructorRepository) {
        this.programInstructorRepository = programInstructorRepository;
    }

    @Override
    public List<SectionInstructorView> findProgramSectionInstructors(Long programSectionId) {
        return programInstructorRepository.findBySectionId(programSectionId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
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

    @Override
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
