package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.dto.CreateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.academicstructure.service.ActiveTermUniquenessGuard;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateTermService implements CreateTermUseCase {

    private final TermRepository termRepository;
    private final ActiveTermUniquenessGuard uniquenessGuard;

    public CreateTermService(TermRepository termRepository, ActiveTermUniquenessGuard uniquenessGuard) {
        this.termRepository = termRepository;
        this.uniquenessGuard = uniquenessGuard;
    }

    @Override
    @Transactional
    public Term create(CreateTermRequest request) {
        uniquenessGuard.ensureNoOtherActiveTerm(request.year(), request.type(), null);
        Term term = new Term();
        term.setYear(request.year());
        term.setDescription(request.description());
        term.setType(request.type());
        term.reschedule(request.startDate(), request.endDate());
        term.defineEnrollmentWindow(request.enrollmentStart(), request.enrollmentEnd());
        term.setStatus(StatusType.ACTIVE);
        return termRepository.save(term);
    }
}
