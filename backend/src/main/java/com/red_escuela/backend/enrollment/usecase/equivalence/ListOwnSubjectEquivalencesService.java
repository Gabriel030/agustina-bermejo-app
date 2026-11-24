package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.OwnSubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.service.SubjectEquivalenceReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListOwnSubjectEquivalencesService implements ListOwnSubjectEquivalencesUseCase {

    private final SubjectEquivalenceReader subjectEquivalenceReader;

    public ListOwnSubjectEquivalencesService(SubjectEquivalenceReader subjectEquivalenceReader) {
        this.subjectEquivalenceReader = subjectEquivalenceReader;
    }

    @Override
    public List<OwnSubjectEquivalenceResponse> list(Long studentId, Long programEnrollmentId) {
        return subjectEquivalenceReader.listOwn(studentId, programEnrollmentId);
    }
}
