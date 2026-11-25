package com.red_escuela.backend.graduation.certificate.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
        String recipientLabel,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> extraMarkers) {

    public CertificateSnapshot {
        extraMarkers = extraMarkers == null ? Map.of() : Map.copyOf(extraMarkers);
    }

    public CertificateSnapshot(String holderFullName, String documentTypeLabel, String documentNumber, String titleKind,
                               String titleName, String programLevel, Integer hours, LocalDate graduationDate,
                               LocalDate issuedOn, String institutionName, List<SignatorySnapshot> signatories,
                               String templateRef, String recipientLabel) {
        this(holderFullName, documentTypeLabel, documentNumber, titleKind, titleName, programLevel, hours, graduationDate,
                issuedOn, institutionName, signatories, templateRef, recipientLabel, Map.of());
    }

    public CertificateSnapshot withRecipientLabel(String label) {
        if (Objects.equals(label, recipientLabel)) {
            return this;
        }
        return new CertificateSnapshot(holderFullName, documentTypeLabel, documentNumber, titleKind, titleName, programLevel,
                hours, graduationDate, issuedOn, institutionName, signatories, templateRef, label, extraMarkers);
    }

    public CertificateSnapshot withExtraMarkers(Map<String, String> markers) {
        return new CertificateSnapshot(holderFullName, documentTypeLabel, documentNumber, titleKind, titleName, programLevel,
                hours, graduationDate, issuedOn, institutionName, signatories, templateRef, recipientLabel, markers);
    }
}
