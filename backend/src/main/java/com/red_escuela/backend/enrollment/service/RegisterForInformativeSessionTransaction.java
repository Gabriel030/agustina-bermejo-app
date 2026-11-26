package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.entity.InformativeSession;
import com.red_escuela.backend.enrollment.entity.InformativeSessionRegistration;
import com.red_escuela.backend.enrollment.exception.AlreadyRegisteredForInformativeSessionException;
import com.red_escuela.backend.enrollment.exception.SessionFullException;
import com.red_escuela.backend.enrollment.repository.InformativeSessionRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RegisterForInformativeSessionTransaction {

    private final InformativeSessionFinder informativeSessionFinder;
    private final InformativeSessionRegistrationRepository registrationRepository;
    private final Clock clock;

    public RegisterForInformativeSessionTransaction(InformativeSessionFinder informativeSessionFinder,
                                                    InformativeSessionRegistrationRepository registrationRepository,
                                                    Clock clock) {
        this.informativeSessionFinder = informativeSessionFinder;
        this.registrationRepository = registrationRepository;
        this.clock = clock;
    }

    @Transactional
    public InformativeSessionResponse register(Long sessionId, Long personId) {
        LocalDateTime now = LocalDateTime.now(clock);
        InformativeSession session = informativeSessionFinder.findForUpdateOrThrow(sessionId);
        session.assertScheduled();
        session.assertNotStarted(now);
        if (registrationRepository.existsBySessionIdAndPersonId(sessionId, personId)) {
            throw new AlreadyRegisteredForInformativeSessionException(sessionId, personId);
        }
        long registrations = registrationRepository.countBySessionId(sessionId);
        if (!session.hasRoomFor(registrations)) {
            throw new SessionFullException(sessionId);
        }
        registrationRepository.save(InformativeSessionRegistration.builder()
                .sessionId(sessionId)
                .personId(personId)
                .createdAt(now)
                .build());
        return InformativeSessionResponse.from(session, registrations + 1, true);
    }
}
