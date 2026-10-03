package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.dto.CreateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateTermService implements CreateTermUseCase {

    private final TermRepository termRepository;

    public CreateTermService(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    @Override
    @Transactional
    public Term create(CreateTermRequest request) {
        Term term = new Term();
        term.setYear(request.year());
        term.setDescription(request.description());
        term.setType(request.type());
        term.reschedule(request.startDate(), request.endDate());
        term.setEnrollmentStart(request.enrollmentStart());
        term.setEnrollmentEnd(request.enrollmentEnd());
        term.setStatus(StatusType.ACTIVE);
        return termRepository.save(term);
    }
}
