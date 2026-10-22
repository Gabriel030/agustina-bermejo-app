package com.red_escuela.backend.billing.note.service;

import java.util.List;

public interface LateFeeDebitNoteLookup {

    List<Long> findDebitNoteIdsOfOriginInvoiceInAscendingOrder(Long originInvoiceId);
}
