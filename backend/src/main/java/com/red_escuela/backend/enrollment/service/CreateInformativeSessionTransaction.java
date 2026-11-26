package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.SaveInformativeSessionRequest;
import com.red_escuela.backend.enrollment.entity.InformativeSession;
import com.red_escuela.backend.enrollment.repository.InformativeSessionRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CreateInformativeSessionTransaction {

    private final InformativeSessionRepository informativeSessionRepository;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public CreateInformativeSessionTransaction(InformativeSessionRepository informativeSessionRepository,
                                               AcademicReferenceFinder academicReferenceFinder,
                                               CurrentActorFinder currentActorFinder, Clock clock) {
        this.informativeSessionRepository = informativeSessionRepository;
        this.academicReferenceFinder = academicReferenceFinder;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public InformativeSessionResponse create(SaveInformativeSessionRequest request) {
        Optional.ofNullable(request.relatedProgramId()).ifPresent(academicReferenceFinder::findProgram);
        InformativeSession session = informativeSessionRepository.save(InformativeSession.builder()
                .title(request.title().trim())
                .scheduledAt(request.scheduledAt())
                .modality(request.modality())
                .meetingUrl(request.meetingUrl())
                .location(request.location())
                .capacity(request.capacity())
                .relatedProgramId(request.relatedProgramId())
                .createdById(currentActorFinder.currentPersonId())
                .createdAt(LocalDateTime.now(clock))
                .build());
        return InformativeSessionResponse.from(session, 0, false);
    }
}
