package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.academicstructure.service.TermFinder;
import com.red_escuela.backend.academicstructure.service.TermScheduleChangeValidator;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateTermService implements UpdateTermUseCase {

    private final TermRepository termRepository;
    private final TermFinder termFinder;
    private final TermScheduleChangeValidator scheduleChangeValidator;

    public UpdateTermService(TermRepository termRepository, TermFinder termFinder, TermScheduleChangeValidator scheduleChangeValidator) {
        this.scheduleChangeValidator = scheduleChangeValidator;
        this.termRepository = termRepository;
        this.termFinder = termFinder;
    }

    @Override
    @Transactional
    public Term update(UpdateTermCommand command) {
        Term term = termFinder.findOrThrow(command.id());
        UpdateTermRequest request = command.request();
        if (request.description() != null) {
            term.setDescription(request.description());
        }
        scheduleChangeValidator.validateScheduleChange(term,
                request.startDate() != null ? request.startDate() : term.getStartDate(),
                request.endDate() != null ? request.endDate() : term.getEndDate());
        term.reschedule(request.startDate(), request.endDate());
        term.defineEnrollmentWindow(request.enrollmentStart(), request.enrollmentEnd());
        return termRepository.save(term);
    }
}
