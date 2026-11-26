package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.entity.InformativeSession;
import com.red_escuela.backend.enrollment.repository.InformativeSessionRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelInformativeSessionTransaction {

    private final InformativeSessionFinder informativeSessionFinder;
    private final InformativeSessionRegistrationRepository registrationRepository;

    public CancelInformativeSessionTransaction(InformativeSessionFinder informativeSessionFinder,
                                               InformativeSessionRegistrationRepository registrationRepository) {
        this.informativeSessionFinder = informativeSessionFinder;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public InformativeSessionResponse cancel(Long sessionId) {
        InformativeSession session = informativeSessionFinder.findForUpdateOrThrow(sessionId);
        session.cancel();
        return InformativeSessionResponse.from(session, registrationRepository.countBySessionId(sessionId), false);
    }
}
