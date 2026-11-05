package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.exception.ExamBoardHasGradesException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExamBoardChangeParticipants {

    private final ExamBoardRegistrationRepository registrationRepository;
    private final GradeItemRepository gradeItemRepository;

    public ExamBoardChangeParticipants(ExamBoardRegistrationRepository registrationRepository, GradeItemRepository gradeItemRepository) {
        this.registrationRepository = registrationRepository;
        this.gradeItemRepository = gradeItemRepository;
    }

    public void requireNoGrades(Long examBoardId) {
        if (!gradeItemRepository.findByExamBoardId(examBoardId).isEmpty()) {
            throw new ExamBoardHasGradesException(examBoardId);
        }
    }

    public String subjectNameOf(ExamBoard examBoard) {
        return examBoard.getSubject() != null ? examBoard.getSubject().getName() : examBoard.getCourseSubject().getName();
    }

    public List<ExamBoardRegistration> registeredOf(Long examBoardId) {
        return registrationRepository.findByExamBoardId(examBoardId).stream()
                .filter(registration -> registration.getStatus() == ExamBoardRegistrationStatus.REGISTERED)
                .toList();
    }

    public List<Long> studentPersonIdsOf(List<ExamBoardRegistration> registrations) {
        return registrations.stream().map(this::studentPersonOf).map(Person::getId).distinct().toList();
    }

    public List<Long> tribunalPersonIdsOf(ExamBoard examBoard) {
        List<ExamBoardInstructor> tribunal = examBoard.getTribunal() == null ? List.of() : examBoard.getTribunal();
        return tribunal.stream().map(assignment -> assignment.getInstructor().getPerson().getId()).distinct().toList();
    }

    private Person studentPersonOf(ExamBoardRegistration registration) {
        return registration.getProgramSubjectSectionEnrollment() != null
                ? registration.getProgramSubjectSectionEnrollment().getStudent().getPerson()
                : registration.getCourseSubjectSectionEnrollment().getStudent().getPerson();
    }
}
