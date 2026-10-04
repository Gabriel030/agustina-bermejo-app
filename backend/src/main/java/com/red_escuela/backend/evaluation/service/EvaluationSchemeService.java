package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.evaluation.exception.InvalidEvaluationSchemeException;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.InvalidGradeException;
import com.red_escuela.backend.evaluation.dto.CreateEvaluationSchemeRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeInUseException;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EvaluationSchemeService {

    private final EvaluationSchemeRepository evaluationSchemeRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;

    public EvaluationSchemeService(
            EvaluationSchemeRepository evaluationSchemeRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSubjectSectionRepository) {
        this.evaluationSchemeRepository = evaluationSchemeRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
    }

    @Transactional
    public EvaluationScheme create(CreateEvaluationSchemeRequest request) {
        EvaluationScheme scheme = new EvaluationScheme();
        applyRequest(scheme, request);
        return evaluationSchemeRepository.save(scheme);
    }

    @Transactional
    public EvaluationScheme update(Long id, CreateEvaluationSchemeRequest request) {
        EvaluationScheme scheme = findOrThrow(id);
        if (isInUse(id)) {
            throw new EvaluationSchemeInUseException(id);
        }
        applyRequest(scheme, request);
        return evaluationSchemeRepository.save(scheme);
    }

    public List<EvaluationScheme> list() {
        return evaluationSchemeRepository.findAll();
    }

    public boolean isInUse(Long id) {
        return subjectSectionRepository.existsByEvaluationSchemeId(id)
                || courseSubjectSectionRepository.existsByEvaluationSchemeId(id);
    }

    private void applyRequest(EvaluationScheme scheme, CreateEvaluationSchemeRequest request) {
        try {
            scheme.define(request.name(), request.examCountExpected(), request.hasFinal(), request.finalWeight(),
                    request.aggregationMethod(), Grade.of(request.minRegularGrade()), Grade.of(request.minApprovalGrade()),
                    Grade.ofNullable(request.promotionMinGrade()), request.notes());
        } catch (InvalidGradeException exception) {
            throw new InvalidEvaluationSchemeException(exception.getMessage());
        }
    }

    private EvaluationScheme findOrThrow(Long id) {
        return evaluationSchemeRepository.findById(id)
                .orElseThrow(() -> new EvaluationSchemeNotFoundException(id));
    }
}
