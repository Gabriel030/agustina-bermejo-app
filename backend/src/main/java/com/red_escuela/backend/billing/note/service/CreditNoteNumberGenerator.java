package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.domain.CreditNoteNumber;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

@Service
public class CreditNoteNumberGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    public CreditNoteNumber nextCreditNoteNumber() {
        Number next = (Number) entityManager.createNativeQuery("select nextval('credit_note_number_seq')").getSingleResult();
        return CreditNoteNumber.formatCreditNoteNumber(next.longValue());
    }
}
