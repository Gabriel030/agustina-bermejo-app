package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.SectionCodeAlreadyExistsException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionCodeGenerator {

    private final EntityManager entityManager;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;

    public SectionCodeGenerator(
            EntityManager entityManager,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository
    ) {
        this.entityManager = entityManager;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
    }

    @Transactional
    public String nextCodeForSubject(String subjectCode, Term term) {
        return reserve(subjectCode, term);
    }

    @Transactional
    public String nextCodeForCourseSubject(Long courseSubjectId, Term term) {
        return reserve("CM" + courseSubjectId, term);
    }

    private String reserve(String prefix, Term term) {
        var period = periodOf(term);
        var sequence = nextSequence(prefix, period);
        var code = prefix + "-" + period + "-" + String.format("%02d", sequence);
        if (subjectSectionRepository.existsByCode(code) || courseSectionRepository.existsByCode(code)) {
            throw new SectionCodeAlreadyExistsException(code);
        }
        return code;
    }

    private int nextSequence(String prefix, String period) {
        var result = entityManager.createNativeQuery(
                        "INSERT INTO section_code_counter (prefix, period, last_value) VALUES (:prefix, :period, 1) "
                                + "ON CONFLICT (prefix, period) DO UPDATE SET last_value = section_code_counter.last_value + 1 "
                                + "RETURNING last_value")
                .setParameter("prefix", prefix)
                .setParameter("period", period)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private String periodOf(Term term) {
        var year = term.getYear() != null ? term.getYear() : term.getStartDate().getYear();
        return year + suffixOf(term.getType());
    }

    private String suffixOf(TermType type) {
        if (type == null) {
            return "A";
        }
        return switch (type) {
            case FIRST_SEMESTER -> "C1";
            case SECOND_SEMESTER -> "C2";
            case INTENSIVE -> "V";
            case ANNUAL -> "A";
        };
    }
}
