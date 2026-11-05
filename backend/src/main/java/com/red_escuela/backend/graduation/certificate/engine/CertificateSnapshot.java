package com.red_escuela.backend.graduation.certificate.engine;

import java.time.LocalDate;
import java.util.List;

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
        String templateRef) {
}
