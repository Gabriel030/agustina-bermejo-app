package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.entity.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.evaluation.dto.SectionInstructorSummary;
import com.red_escuela.backend.evaluation.exception.NotOwnSectionException;
import com.red_escuela.backend.evaluation.exception.NotSectionLeadInstructorException;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SectionEvaluationConfigInstructorAccess {

    private final SubjectSectionInstructorRepository sectionInstructorRepository;

    public SectionEvaluationConfigInstructorAccess(SubjectSectionInstructorRepository sectionInstructorRepository) {
        this.sectionInstructorRepository = sectionInstructorRepository;
    }

    public void requireAssignedToSection(Long sectionId, Long instructorId) {
        if (!sectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
    }

    public void requireLeadInstructor(Long sectionId, Long instructorId) {
        requireAssignedToSection(sectionId, instructorId);
        if (!isLeadInstructor(sectionId, instructorId)) {
            throw new NotSectionLeadInstructorException(instructorId, sectionId);
        }
    }

    public boolean isLeadInstructor(Long sectionId, Long instructorId) {
        return sectionInstructorRepository.existsBySectionIdAndInstructorIdAndRole(sectionId, instructorId, InstructorRole.LEAD);
    }

    public List<SectionInstructorSummary> listInstructors(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId).stream()
                .map(SectionInstructorSummary::from)
                .toList();
    }

    public Person leadInstructorPerson(SubjectSection section) {
        return sectionInstructorRepository.findBySectionId(section.getId()).stream()
                .filter(instructor -> instructor.getRole() == InstructorRole.LEAD)
                .findFirst()
                .map(instructor -> instructor.getInstructor().getPerson())
                .orElseThrow(() -> new NotOwnSectionException(null, section.getId()));
    }
}
