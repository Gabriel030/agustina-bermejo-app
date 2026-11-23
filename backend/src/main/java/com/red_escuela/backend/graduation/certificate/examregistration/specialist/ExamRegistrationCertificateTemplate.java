package com.red_escuela.backend.graduation.certificate.examregistration.specialist;

import com.red_escuela.backend.evaluation.api.OwnExamRegistrationView;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateDefinition;
import com.red_escuela.backend.graduation.certificate.engine.PageFormat;
import com.red_escuela.backend.graduation.certificate.engine.Rect;
import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class ExamRegistrationCertificateTemplate {

    public static final String EXAM_REGISTRATION_BUILT_IN_V1 = "EXAM_REGISTRATION_BUILT_IN_V1";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GraduationSnapshotFactory snapshotFactory;

    public ExamRegistrationCertificateTemplate(GraduationSnapshotFactory snapshotFactory) {
        this.snapshotFactory = snapshotFactory;
    }

    public CertificateSnapshot snapshotOf(Student student, OwnExamRegistrationView registration) {
        CertificateSnapshot base = snapshotFactory.create(student, "la mesa de examen de", registration.subjectName(),
                registration.careerName(), null, registration.examDateTime().toLocalDate());
        return new CertificateSnapshot(base.holderFullName(), base.documentTypeLabel(), base.documentNumber(), base.titleKind(),
                base.titleName(), base.programLevel(), base.hours(), base.graduationDate(), base.issuedOn(),
                base.institutionName(), base.signatories(), EXAM_REGISTRATION_BUILT_IN_V1);
    }

    public CertificateTemplateDefinition definitionFor(CertificateSnapshot snapshot, RecipientType recipientType, String recipientText) {
        String body = "Se certifica que {holder}, {documentType} {documentNumber}, se encuentra inscripto/a en {titleKind} {titleName}"
                + " (" + snapshot.programLevel() + ") de " + snapshot.institutionName()
                + ", a rendirse el {graduationDate}." + recipientSentence(recipientType, recipientText)
                + " Válida hasta el día del examen, " + snapshot.graduationDate().format(DATE_FORMAT) + ".";
        return new CertificateTemplateDefinition(EXAM_REGISTRATION_BUILT_IN_V1, PageFormat.A4_LANDSCAPE,
                "CONSTANCIA DE INSCRIPCIÓN A EXAMEN", body,
                new Rect(100, 150, 642, 280), new Rect(690, 60, 90, 90), new Rect(430, 60, 250, 40), new Rect(100, 60, 260, 80));
    }

    private String recipientSentence(RecipientType recipientType, String recipientText) {
        if (recipientType == null) {
            return "";
        }
        return " Se extiende a pedido del/la interesado/a para ser presentada ante " + label(recipientType) + ": " + recipientText + ".";
    }

    private String label(RecipientType recipientType) {
        return switch (recipientType) {
            case HEALTH_INSURANCE -> "su obra social o prepaga";
            case EMPLOYER -> "su empleador";
            case STUDENT_TRANSPORT -> "la empresa de transporte estudiantil";
            case OTHER -> "otra entidad";
        };
    }
}
