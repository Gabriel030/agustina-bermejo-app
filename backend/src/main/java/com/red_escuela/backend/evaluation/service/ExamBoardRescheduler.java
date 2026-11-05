package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.HolidayFinder;
import com.red_escuela.backend.evaluation.dto.RescheduleExamBoardRequest;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.events.ExamBoardRescheduledEvent;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamDateOnHolidayException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ExamBoardRescheduler {

    private final ExamBoardRepository examBoardRepository;
    private final HolidayFinder holidayFinder;
    private final ExamBoardChangeParticipants participants;
    private final ApplicationEventPublisher eventPublisher;

    public ExamBoardRescheduler(ExamBoardRepository examBoardRepository, HolidayFinder holidayFinder,
                                ExamBoardChangeParticipants participants, ApplicationEventPublisher eventPublisher) {
        this.examBoardRepository = examBoardRepository;
        this.holidayFinder = holidayFinder;
        this.participants = participants;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ExamBoard reschedule(Long examBoardId, RescheduleExamBoardRequest request) {
        ExamBoard examBoard = examBoardRepository.findById(examBoardId).orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
        if (holidayFinder.isHoliday(request.examDate().toLocalDate())) {
            throw new ExamDateOnHolidayException(request.examDate().toLocalDate());
        }
        LocalDateTime oldExamDate = examBoard.getExamDate();
        examBoard.reschedule(request.examDate(), request.registrationStart(), request.registrationEnd(), request.classroom());
        participants.requireNoGrades(examBoardId);
        ExamBoard saved = examBoardRepository.save(examBoard);
        eventPublisher.publishEvent(ExamBoardRescheduledEvent.rescheduledFor(examBoardId, participants.subjectNameOf(saved), oldExamDate,
                saved.getExamDate(), saved.getClassroom(),
                participants.studentPersonIdsOf(participants.registeredOf(examBoardId)), participants.tribunalPersonIdsOf(saved)));
        return saved;
    }
}
