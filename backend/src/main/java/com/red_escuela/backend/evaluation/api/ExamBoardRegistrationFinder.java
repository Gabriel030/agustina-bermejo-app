package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ExamBoardRegistrationFinder {

    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;

    public ExamBoardRegistrationFinder(ExamBoardRegistrationRepository examBoardRegistrationRepository) {
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
    }

    public ExamBoardRegistrationView requireById(Long id) {
        ExamBoardRegistration registration = examBoardRegistrationRepository.findById(id)
                .orElseThrow(() -> new ExamBoardRegistrationNotFoundException(id));
        return new ExamBoardRegistrationView(registration.getId(), registration.getExamBoard().getId());
    }
}
