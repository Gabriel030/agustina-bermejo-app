package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.academicstructure.exception.SubjectRecurrenceIncompatibleWithTermException;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;

@Component
public class SubjectOfferingGuard {

    private final ProgramSubjectRepository programSubjectRepository;

    public SubjectOfferingGuard(ProgramSubjectRepository programSubjectRepository) {
        this.programSubjectRepository = programSubjectRepository;
    }

    public void requireOfferableInProgram(Long programId, Subject subject, Term term) {
        subject.requireOpenableForSections();
        ProgramSubject planEntry = programSubjectRepository.findByProgramIdAndSubjectId(programId, subject.getId())
                .filter(entry -> entry.getStatus() == null || entry.getStatus() == StatusType.ACTIVE)
                .orElseThrow(() -> new SubjectNotInProgramPlanException(subject.getId(), programId));
        requireRecurrenceCompatible(subject, planEntry.getSubjectRecurrence(), term.getType());
    }

    private void requireRecurrenceCompatible(Subject subject, SubjectRecurrence recurrence, TermType termType) {
        if (recurrence == SubjectRecurrence.ANNUAL && termType != TermType.ANNUAL) {
            throw new SubjectRecurrenceIncompatibleWithTermException(subject.getId(), recurrence, termType);
        }
    }
}
