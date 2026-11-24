package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.service.SubjectEquivalenceReader;
import com.red_escuela.backend.enrollment.service.VoidSubjectEquivalenceTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VoidSubjectEquivalenceService implements VoidSubjectEquivalenceUseCase {

    private final VoidSubjectEquivalenceTransaction voidSubjectEquivalenceTransaction;
    private final SubjectEquivalenceReader subjectEquivalenceReader;

    public VoidSubjectEquivalenceService(VoidSubjectEquivalenceTransaction voidSubjectEquivalenceTransaction,
                                         SubjectEquivalenceReader subjectEquivalenceReader) {
        this.voidSubjectEquivalenceTransaction = voidSubjectEquivalenceTransaction;
        this.subjectEquivalenceReader = subjectEquivalenceReader;
    }

    @Override
    public SubjectEquivalenceResponse voidEquivalence(VoidSubjectEquivalenceCommand command) {
        voidSubjectEquivalenceTransaction.voidEquivalence(command.programEnrollmentId(), command.equivalenceId(), command.reason());
        return subjectEquivalenceReader.get(command.programEnrollmentId(), command.equivalenceId());
    }
}
