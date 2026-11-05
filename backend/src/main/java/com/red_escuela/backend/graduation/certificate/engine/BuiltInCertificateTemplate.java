package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import org.springframework.stereotype.Component;

@Component
public class BuiltInCertificateTemplate implements CertificateTemplateResolver {

    public static final String BUILT_IN_V1 = "BUILT_IN_V1";

    private static final String BODY = "Se certifica que {holder}, {documentType} {documentNumber}, ha cumplido con todos los requisitos"
            + " académicos de {titleKind} {titleName}{hours}, con fecha de egreso {graduationDate}.";

    private static final CertificateTemplateDefinition DEFINITION = new CertificateTemplateDefinition(
            BUILT_IN_V1,
            PageFormat.A4_LANDSCAPE,
            "CERTIFICADO DE EGRESO",
            BODY,
            new Rect(100, 150, 642, 280),
            new Rect(690, 60, 90, 90),
            new Rect(430, 60, 250, 40),
            new Rect(100, 60, 260, 80));

    @Override
    public CertificateTemplateDefinition resolve(GraduationCertificateKind kind, Long titleId) {
        return DEFINITION;
    }
}
