package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.TermScheduleChangeValidator;
import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermUpdate {

    private final TermRepository termRepository;
    private final TermFinder termFinder;
    private final TermScheduleChangeValidator scheduleChangeValidator;

    public TermUpdate(TermRepository termRepository, TermFinder termFinder, TermScheduleChangeValidator scheduleChangeValidator) {
        this.scheduleChangeValidator = scheduleChangeValidator;
        this.termRepository = termRepository;
        this.termFinder = termFinder;
    }

    @Transactional
    public Term update(Long id, UpdateTermRequest request) {
        Term term = termFinder.findOrThrow(id);
        if (request.description() != null) {
            term.setDescription(request.description());
        }
        scheduleChangeValidator.validateScheduleChange(term.getId(), term.getStartDate(), term.getEndDate(),
                request.startDate() != null ? request.startDate() : term.getStartDate(),
                request.endDate() != null ? request.endDate() : term.getEndDate());
        term.reschedule(request.startDate(), request.endDate());
        term.defineEnrollmentWindow(request.enrollmentStart(), request.enrollmentEnd());
        return termRepository.save(term);
    }
}
