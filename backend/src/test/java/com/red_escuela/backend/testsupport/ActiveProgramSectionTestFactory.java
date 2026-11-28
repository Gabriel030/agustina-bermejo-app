package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.academicstructure.api.SectionEvaluationSetupPort;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.service.SectionIdentityAssigner;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ActiveProgramSectionTestFactory {

    private final SubjectSectionRepository sectionRepository;
    private final SectionIdentityAssigner identityAssigner;
    private final SectionEvaluationSetupPort evaluationSetupPort;
    private final EntityManager entityManager;
    private final com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository courseSectionRepository;

    public ActiveProgramSectionTestFactory(SubjectSectionRepository sectionRepository,
                                           SectionIdentityAssigner identityAssigner,
                                           SectionEvaluationSetupPort evaluationSetupPort,
                                           EntityManager entityManager,
                                           com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository courseSectionRepository) {
        this.courseSectionRepository = courseSectionRepository;
        this.sectionRepository = sectionRepository;
        this.identityAssigner = identityAssigner;
        this.evaluationSetupPort = evaluationSetupPort;
        this.entityManager = entityManager;
    }

    public SubjectSection create(Subject subject, Term term, String name, int capacity, Long evaluationSchemeId) {
        SubjectSection section = new SubjectSection();
        section.setSubject(subject);
        section.setTerm(term);
        section.setName(name);
        section.setCapacity(capacity);
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        identityAssigner.identifyNewSubjectSection(section, null, null);
        if (evaluationSchemeId != null) {
            evaluationSetupPort.requireEvaluationSchemeExists(evaluationSchemeId);
            section.setEvaluationSchemeId(evaluationSchemeId);
        }
        SubjectSection saved = sectionRepository.save(section);
        evaluationSetupPort.createInitialEvaluationDraft(saved);
        return saved;
    }

    public com.red_escuela.backend.academicstructure.entity.CourseSubjectSection createCourseSection(
            com.red_escuela.backend.academicstructure.entity.CourseSubject courseSubject, Term term, String name, int capacity) {
        com.red_escuela.backend.academicstructure.entity.CourseSubjectSection section = new com.red_escuela.backend.academicstructure.entity.CourseSubjectSection();
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setName(name);
        section.setCapacity(capacity);
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        identityAssigner.identifyNewCourseSection(section, null, null);
        return courseSectionRepository.save(section);
    }
}
