package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionClosureStatusPort;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SectionClosureStatusPortAdapter implements SectionClosureStatusPort {

    private final CommissionClosureRepository closureRepository;

    public SectionClosureStatusPortAdapter(CommissionClosureRepository closureRepository) {
        this.closureRepository = closureRepository;
    }

    @Override
    public boolean isClosed(SectionKind kind, Long sectionId) {
        return hasStatus(kind, sectionId, ClosureStatus.CLOSED);
    }

    @Override
    public boolean isReopened(SectionKind kind, Long sectionId) {
        return hasStatus(kind, sectionId, ClosureStatus.REOPENED);
    }

    private boolean hasStatus(SectionKind kind, Long sectionId, ClosureStatus status) {
        return closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .map(closure -> closure.getStatus() == status)
                .orElse(false);
    }
}
