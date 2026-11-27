package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.exception.SectionClosureClosedException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class CommissionClosureGuard {

    private final CommissionClosureRepository closureRepository;

    public CommissionClosureGuard(CommissionClosureRepository closureRepository) {
        this.closureRepository = closureRepository;
    }

    public void requireNotClosed(SectionKind kind, Long sectionId) {
        if (hasStatus(kind, sectionId, ClosureStatus.CLOSED)) {
            throw new SectionClosureClosedException(sectionId);
        }
    }

    public boolean isReopened(SectionKind kind, Long sectionId) {
        return hasStatus(kind, sectionId, ClosureStatus.REOPENED);
    }

    private boolean hasStatus(SectionKind kind, Long sectionId, ClosureStatus status) {
        return closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .map(closure -> closure.getStatus() == status)
                .orElse(false);
    }
}
