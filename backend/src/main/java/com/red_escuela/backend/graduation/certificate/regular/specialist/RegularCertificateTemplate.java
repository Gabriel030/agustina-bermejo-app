package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateDefinition;
import com.red_escuela.backend.graduation.certificate.engine.PageFormat;
import com.red_escuela.backend.graduation.certificate.engine.Rect;
import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class RegularCertificateTemplate {

    public static final String REGULAR_BUILT_IN_V1 = "REGULAR_BUILT_IN_V1";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GraduationSnapshotFactory snapshotFactory;

    public RegularCertificateTemplate(GraduationSnapshotFactory snapshotFactory) {
        this.snapshotFactory = snapshotFactory;
    }

    public CertificateSnapshot snapshotOf(RegularEnrollmentFacts facts, LocalDate issuedOn) {
        CertificateSnapshot base = snapshotFactory.create(facts.student(), facts.titleKind(), facts.titleName(), null, null, issuedOn);
        return new CertificateSnapshot(base.holderFullName(), base.documentTypeLabel(), base.documentNumber(), base.titleKind(),
                base.titleName(), base.programLevel(), base.hours(), base.graduationDate(), base.issuedOn(),
                base.institutionName(), base.signatories(), REGULAR_BUILT_IN_V1);
    }

    public CertificateTemplateDefinition definitionFor(CertificateSnapshot snapshot, LocalDate validUntil) {
        String body = "Se certifica que {holder}, {documentType} {documentNumber}, es alumno/a regular de {titleKind} {titleName}"
                + " de " + snapshot.institutionName() + " al {graduationDate}. Válido hasta " + validUntil.format(DATE_FORMAT) + ".";
        return new CertificateTemplateDefinition(REGULAR_BUILT_IN_V1, PageFormat.A4_LANDSCAPE, "CONSTANCIA DE ALUMNO REGULAR", body,
                new Rect(100, 150, 642, 280), new Rect(690, 60, 90, 90), new Rect(430, 60, 250, 40), new Rect(100, 60, 260, 80));
    }
}
