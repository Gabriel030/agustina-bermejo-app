package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.LegalDocumentType;

import java.time.LocalDateTime;

public interface LegalAcceptanceRow {
    Long getId();

    Long getPersonId();

    String getPersonName();

    String getPersonLastname();

    LegalDocumentType getDocumentType();

    String getVersion();

    LocalDateTime getAcceptedAt();
}
