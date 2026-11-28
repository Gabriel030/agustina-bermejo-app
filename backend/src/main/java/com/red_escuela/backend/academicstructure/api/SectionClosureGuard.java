package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.exception.SectionClosureClosedException;
import org.springframework.stereotype.Component;

@Component
public class SectionClosureGuard {

    private final SectionClosureStatusPort closureStatusPort;

    public SectionClosureGuard(SectionClosureStatusPort closureStatusPort) {
        this.closureStatusPort = closureStatusPort;
    }

    public void requireNotClosed(SectionKind kind, Long sectionId) {
        if (closureStatusPort.isClosed(kind, sectionId)) {
            throw new SectionClosureClosedException(sectionId);
        }
    }

    public boolean isClosed(SectionKind kind, Long sectionId) {
        return closureStatusPort.isClosed(kind, sectionId);
    }

    public boolean isReopened(SectionKind kind, Long sectionId) {
        return closureStatusPort.isReopened(kind, sectionId);
    }
}
