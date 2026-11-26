package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.SaveInformativeSessionRequest;
import com.red_escuela.backend.enrollment.entity.InformativeSession;
import com.red_escuela.backend.enrollment.exception.InformativeSessionCapacityBelowRegistrationsException;
import com.red_escuela.backend.enrollment.repository.InformativeSessionRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UpdateInformativeSessionTransaction {

    private final InformativeSessionFinder informativeSessionFinder;
    private final InformativeSessionRegistrationRepository registrationRepository;
    private final AcademicReferenceFinder academicReferenceFinder;

    public UpdateInformativeSessionTransaction(InformativeSessionFinder informativeSessionFinder,
                                               InformativeSessionRegistrationRepository registrationRepository,
                                               AcademicReferenceFinder academicReferenceFinder) {
        this.informativeSessionFinder = informativeSessionFinder;
        this.registrationRepository = registrationRepository;
        this.academicReferenceFinder = academicReferenceFinder;
    }

    @Transactional
    public InformativeSessionResponse update(Long sessionId, SaveInformativeSessionRequest request) {
        InformativeSession session = informativeSessionFinder.findForUpdateOrThrow(sessionId);
        session.assertScheduled();
        Optional.ofNullable(request.relatedProgramId()).ifPresent(academicReferenceFinder::findProgram);
        long registrations = registrationRepository.countBySessionId(sessionId);
        Optional.ofNullable(request.capacity())
                .filter(capacity -> capacity < registrations)
                .ifPresent(capacity -> {
                    throw new InformativeSessionCapacityBelowRegistrationsException(sessionId, capacity, registrations);
                });
        session.setTitle(request.title().trim());
        session.setScheduledAt(request.scheduledAt());
        session.setModality(request.modality());
        session.setMeetingUrl(request.meetingUrl());
        session.setLocation(request.location());
        session.setCapacity(request.capacity());
        session.setRelatedProgramId(request.relatedProgramId());
        return InformativeSessionResponse.from(session, registrations, false);
    }
}
