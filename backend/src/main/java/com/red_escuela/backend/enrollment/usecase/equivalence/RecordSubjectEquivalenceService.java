package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.service.RecordSubjectEquivalenceTransaction;
import com.red_escuela.backend.enrollment.service.SubjectEquivalenceReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RecordSubjectEquivalenceService implements RecordSubjectEquivalenceUseCase {

    private final RecordSubjectEquivalenceTransaction recordSubjectEquivalenceTransaction;
    private final SubjectEquivalenceReader subjectEquivalenceReader;

    public RecordSubjectEquivalenceService(RecordSubjectEquivalenceTransaction recordSubjectEquivalenceTransaction,
                                           SubjectEquivalenceReader subjectEquivalenceReader) {
        this.recordSubjectEquivalenceTransaction = recordSubjectEquivalenceTransaction;
        this.subjectEquivalenceReader = subjectEquivalenceReader;
    }

    @Override
    public SubjectEquivalenceResponse record(RecordSubjectEquivalenceCommand command) {
        Long equivalenceId = recordSubjectEquivalenceTransaction.record(command.programEnrollmentId(), command.request());
        return subjectEquivalenceReader.get(command.programEnrollmentId(), equivalenceId);
    }
}
