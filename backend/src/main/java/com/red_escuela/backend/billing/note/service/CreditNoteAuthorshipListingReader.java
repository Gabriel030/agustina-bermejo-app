package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.CreditNoteAuthorshipTrace;
import com.red_escuela.backend.billing.note.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.note.service.CreditNoteAuthorshipReader;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreditNoteAuthorshipListingReader {
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteAuthorshipReader authorshipReader;

    public CreditNoteAuthorshipListingReader(CreditNoteRepository creditNoteRepository, CreditNoteAuthorshipReader authorshipReader) {
        this.creditNoteRepository = creditNoteRepository;
        this.authorshipReader = authorshipReader;
    }

    @Transactional(readOnly = true)
    public List<CreditNoteResponse> listCreditNotesOfStudentWithAuthorship(Long studentId) {
        List<CreditNote> notes = creditNoteRepository.findByStudentIdNewestFirst(studentId);
        Map<Long, CreditNoteAuthorshipTrace> traces = authorshipReader.describeAuthorshipOf(notes);
        return notes.stream().map(note -> CreditNoteResponse.from(note, traces.getOrDefault(note.getId(), CreditNoteAuthorshipTrace.unknown()))).toList();
    }
}
