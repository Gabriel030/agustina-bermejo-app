package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.note.service.StudentCreditNoteListingReader;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class ListCreditNotesOfStudentService implements ListCreditNotesOfStudentUseCase {

    private final StudentCreditNoteListingReader studentCreditNoteListingReader;

    public ListCreditNotesOfStudentService(StudentCreditNoteListingReader studentCreditNoteListingReader) {
        this.studentCreditNoteListingReader = studentCreditNoteListingReader;
    }

    @Override
    public List<CreditNoteResponse> listCreditNotesOfStudent(Long studentId) {
        return studentCreditNoteListingReader.listCreditNotesOfStudent(studentId);
    }
}
