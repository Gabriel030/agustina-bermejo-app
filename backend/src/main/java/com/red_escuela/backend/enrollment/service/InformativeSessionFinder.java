package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.InformativeSession;
import com.red_escuela.backend.enrollment.exception.InformativeSessionAlreadyStartedException;
import com.red_escuela.backend.enrollment.exception.InformativeSessionNotFoundException;
import com.red_escuela.backend.enrollment.exception.InformativeSessionNotScheduledException;
import com.red_escuela.backend.enrollment.repository.InformativeSessionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class InformativeSessionFinder {

    private final InformativeSessionRepository informativeSessionRepository;

    public InformativeSessionFinder(InformativeSessionRepository informativeSessionRepository) {
        this.informativeSessionRepository = informativeSessionRepository;
    }

    public InformativeSession findForUpdateOrThrow(Long id) {
        return informativeSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new InformativeSessionNotFoundException(id));
    }

    public void requireScheduled(InformativeSession session) {
        if (!session.isScheduled()) {
            throw new InformativeSessionNotScheduledException(session.getId());
        }
    }

    public void requireNotStarted(InformativeSession session, LocalDateTime now) {
        if (session.hasStartedBy(now)) {
            throw new InformativeSessionAlreadyStartedException(session.getId());
        }
    }
}
