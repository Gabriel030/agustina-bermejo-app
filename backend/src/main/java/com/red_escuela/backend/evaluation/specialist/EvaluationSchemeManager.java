package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.exception.InvalidEvaluationSchemeException;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.InvalidGradeException;
import com.red_escuela.backend.evaluation.dto.CreateEvaluationSchemeRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeInUseException;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeNotFoundException;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EvaluationSchemeManager {

    private final EvaluationSchemeRepository evaluationSchemeRepository;
    private final SectionFinder sectionFinder;

    public EvaluationSchemeManager(
            EvaluationSchemeRepository evaluationSchemeRepository,
            SectionFinder sectionFinder) {
        this.evaluationSchemeRepository = evaluationSchemeRepository;
        this.sectionFinder = sectionFinder;
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
        return sectionFinder.isEvaluationSchemeAssigned(id);
    }

    private void applyRequest(EvaluationScheme scheme, CreateEvaluationSchemeRequest request) {
        int recoveryQuota = request.recoveryQuota() == null ? EvaluationScheme.DEFAULT_RECOVERY_QUOTA : request.recoveryQuota();
        if (recoveryQuota < 0 || recoveryQuota > EvaluationScheme.MAX_RECOVERY_QUOTA) {
            throw new InvalidEvaluationSchemeException(
                    "la cantidad de recuperatorios va de 0 a " + EvaluationScheme.MAX_RECOVERY_QUOTA);
        }
        try {
            scheme.define(request.name(), request.examCountExpected(), request.hasFinal(), request.finalWeight(),
                    request.aggregationMethod(), Grade.of(request.minRegularGrade()), Grade.of(request.minApprovalGrade()),
                    Grade.ofNullable(request.promotionMinGrade()), request.notes());
            scheme.setRecoveryQuota(recoveryQuota);
        } catch (InvalidGradeException exception) {
            throw new InvalidEvaluationSchemeException(exception.getMessage());
        }
    }

    private EvaluationScheme findOrThrow(Long id) {
        return evaluationSchemeRepository.findById(id)
                .orElseThrow(() -> new EvaluationSchemeNotFoundException(id));
    }
}
