package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceStatusFilter;
import com.red_escuela.backend.enrollment.service.SubjectEquivalenceReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListSubjectEquivalencesService implements ListSubjectEquivalencesUseCase {

    private final SubjectEquivalenceReader subjectEquivalenceReader;

    public ListSubjectEquivalencesService(SubjectEquivalenceReader subjectEquivalenceReader) {
        this.subjectEquivalenceReader = subjectEquivalenceReader;
    }

    @Override
    public List<SubjectEquivalenceResponse> list(Long programEnrollmentId, SubjectEquivalenceStatusFilter filter) {
        return subjectEquivalenceReader.list(programEnrollmentId, filter);
    }
}
