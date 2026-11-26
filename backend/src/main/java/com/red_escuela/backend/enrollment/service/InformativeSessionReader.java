package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.ListInformativeSessionsQuery;
import com.red_escuela.backend.enrollment.entity.InformativeSession;
import com.red_escuela.backend.enrollment.entity.InformativeSessionStatus;
import com.red_escuela.backend.enrollment.repository.InformativeSessionRegistrationRepository;
import com.red_escuela.backend.enrollment.repository.InformativeSessionRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InformativeSessionReader {

    private final InformativeSessionRepository informativeSessionRepository;
    private final InformativeSessionRegistrationRepository registrationRepository;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public InformativeSessionReader(InformativeSessionRepository informativeSessionRepository,
                                    InformativeSessionRegistrationRepository registrationRepository,
                                    CurrentActorFinder currentActorFinder, Clock clock) {
        this.informativeSessionRepository = informativeSessionRepository;
        this.registrationRepository = registrationRepository;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    public List<InformativeSessionResponse> list(ListInformativeSessionsQuery query) {
        InformativeSessionStatus status = Optional.ofNullable(query.status()).orElse(InformativeSessionStatus.SCHEDULED);
        LocalDateTime from = Optional.ofNullable(query.from()).map(LocalDate::atStartOfDay).orElseGet(() -> LocalDateTime.now(clock));
        Long personId = currentActorFinder.currentPersonId();
        List<InformativeSession> sessions = informativeSessionRepository.findByStatusScheduledFrom(status, from);
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = sessions.stream().map(InformativeSession::getId).toList();
        Map<Long, Long> totals = registrationRepository.countBySessionIds(ids).stream()
                .collect(Collectors.toMap(total -> total.getSessionId(), total -> total.getTotal()));
        Set<Long> registeredByPerson = new HashSet<>(registrationRepository.findSessionIdsRegisteredBy(personId, ids));
        return sessions.stream()
                .map(session -> InformativeSessionResponse.from(session, totals.getOrDefault(session.getId(), 0L),
                        registeredByPerson.contains(session.getId())))
                .toList();
    }
}
