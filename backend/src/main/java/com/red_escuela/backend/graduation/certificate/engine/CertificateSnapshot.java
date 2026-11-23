package com.red_escuela.backend.graduation.certificate.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record CertificateSnapshot(
        String holderFullName,
        String documentTypeLabel,
        String documentNumber,
        String titleKind,
        String titleName,
        String programLevel,
        Integer hours,
        LocalDate graduationDate,
        LocalDate issuedOn,
        String institutionName,
        List<SignatorySnapshot> signatories,
        String templateRef,
        String recipientLabel) {

    public CertificateSnapshot(String holderFullName, String documentTypeLabel, String documentNumber, String titleKind,
                               String titleName, String programLevel, Integer hours, LocalDate graduationDate, LocalDate issuedOn,
                               String institutionName, List<SignatorySnapshot> signatories, String templateRef) {
        this(holderFullName, documentTypeLabel, documentNumber, titleKind, titleName, programLevel, hours, graduationDate,
                issuedOn, institutionName, signatories, templateRef, null);
    }

    public CertificateSnapshot withRecipientLabel(String label) {
        if (Objects.equals(label, recipientLabel)) {
            return this;
        }
        return new CertificateSnapshot(holderFullName, documentTypeLabel, documentNumber, titleKind, titleName, programLevel,
                hours, graduationDate, issuedOn, institutionName, signatories, templateRef, label);
    }
}
