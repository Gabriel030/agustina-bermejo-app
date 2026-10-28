package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.CreditNoteAuthorshipTrace;
import com.red_escuela.backend.billing.note.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.note.service.CreditNoteAuthorshipReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@UseCase
public class ListCreditNotesOfStudentWithAuthorshipService implements ListCreditNotesOfStudentWithAuthorshipUseCase {
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteAuthorshipReader authorshipReader;

    public ListCreditNotesOfStudentWithAuthorshipService(CreditNoteRepository creditNoteRepository, CreditNoteAuthorshipReader authorshipReader) {
        this.creditNoteRepository = creditNoteRepository;
        this.authorshipReader = authorshipReader;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @Transactional(readOnly = true)
    public List<CreditNoteResponse> listCreditNotesOfStudentWithAuthorship(Long studentId) {
        List<CreditNote> notes = creditNoteRepository.findByStudentIdNewestFirst(studentId);
        Map<Long, CreditNoteAuthorshipTrace> traces = authorshipReader.describeAuthorshipOf(notes);
        return notes.stream().map(note -> CreditNoteResponse.from(note, traces.getOrDefault(note.getId(), CreditNoteAuthorshipTrace.unknown()))).toList();
    }
}
