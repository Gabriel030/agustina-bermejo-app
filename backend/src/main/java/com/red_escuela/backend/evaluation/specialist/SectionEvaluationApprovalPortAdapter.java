package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionEvaluationApprovalPort;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SectionEvaluationApprovalPortAdapter implements SectionEvaluationApprovalPort {

    private final SectionEvaluationConfigRepository configRepository;

    public SectionEvaluationApprovalPortAdapter(SectionEvaluationConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public Set<Long> programSectionIdsWithApprovedEvaluation(Collection<Long> programSectionIds) {
        if (programSectionIds.isEmpty()) {
            return Set.of();
        }
        return configRepository.findBySubjectSectionIdInAndStatus(programSectionIds, SectionEvaluationConfigStatus.APPROVED).stream()
                .map(config -> config.getSubjectSection().getId())
                .collect(Collectors.toUnmodifiableSet());
    }
}
