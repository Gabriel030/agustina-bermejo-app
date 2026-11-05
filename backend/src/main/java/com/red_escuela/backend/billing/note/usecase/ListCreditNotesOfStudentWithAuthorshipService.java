package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.note.service.CreditNoteAuthorshipListingReader;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class ListCreditNotesOfStudentWithAuthorshipService implements ListCreditNotesOfStudentWithAuthorshipUseCase {

    private final CreditNoteAuthorshipListingReader creditNoteAuthorshipListingReader;

    public ListCreditNotesOfStudentWithAuthorshipService(CreditNoteAuthorshipListingReader creditNoteAuthorshipListingReader) {
        this.creditNoteAuthorshipListingReader = creditNoteAuthorshipListingReader;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<CreditNoteResponse> listCreditNotesOfStudentWithAuthorship(Long studentId) {
        return creditNoteAuthorshipListingReader.listCreditNotesOfStudentWithAuthorship(studentId);
    }
}
