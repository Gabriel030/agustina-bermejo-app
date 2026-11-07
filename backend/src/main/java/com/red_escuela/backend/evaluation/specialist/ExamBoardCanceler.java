package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.events.ExamBoardCanceledEvent;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExamBoardCanceler {

    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardRegistrationRepository registrationRepository;
    private final ExamBoardChangeChecker participants;
    private final ApplicationEventPublisher eventPublisher;

    public ExamBoardCanceler(ExamBoardRepository examBoardRepository, ExamBoardRegistrationRepository registrationRepository,
                             ExamBoardChangeChecker participants, ApplicationEventPublisher eventPublisher) {
        this.examBoardRepository = examBoardRepository;
        this.registrationRepository = registrationRepository;
        this.participants = participants;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ExamBoardResponse cancel(Long examBoardId, String reason) {
        ExamBoard examBoard = examBoardRepository.findById(examBoardId).orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
        participants.requireScheduled(examBoard);
        participants.requireNoGrades(examBoardId);
        examBoard.cancel(ApplicationTime.now(), reason);
        List<ExamBoardRegistration> registered = participants.registeredOf(examBoardId);
        List<Long> studentPersonIds = participants.studentPersonIdsOf(registered);
        registered.forEach(registration -> registration.setStatus(ExamBoardRegistrationStatus.WITHDRAWN));
        registrationRepository.saveAll(registered);
        ExamBoard saved = examBoardRepository.save(examBoard);
        eventPublisher.publishEvent(ExamBoardCanceledEvent.canceledFor(examBoardId, participants.subjectNameOf(saved), saved.getExamDate(),
                studentPersonIds, participants.tribunalPersonIdsOf(saved), reason));
        return ExamBoardResponse.from(saved);
    }
}
