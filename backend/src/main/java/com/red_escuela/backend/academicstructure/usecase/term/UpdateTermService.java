package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.academicstructure.service.TermFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateTermService implements UpdateTermUseCase {

    private final TermRepository termRepository;
    private final TermFinder termFinder;

    public UpdateTermService(TermRepository termRepository, TermFinder termFinder) {
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
        if (request.startDate() != null) {
            term.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            term.setEndDate(request.endDate());
        }
        if (request.enrollmentStart() != null) {
            term.setEnrollmentStart(request.enrollmentStart());
        }
        if (request.enrollmentEnd() != null) {
            term.setEnrollmentEnd(request.enrollmentEnd());
        }
        return termRepository.save(term);
    }
}
