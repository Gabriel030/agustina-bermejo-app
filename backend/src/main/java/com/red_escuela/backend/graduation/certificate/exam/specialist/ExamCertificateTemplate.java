package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateDefinition;
import com.red_escuela.backend.graduation.certificate.engine.PageFormat;
import com.red_escuela.backend.graduation.certificate.engine.Rect;
import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
import com.red_escuela.backend.identity.entity.Student;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class ExamCertificateTemplate {

    public static final String EXAM_BUILT_IN_V1 = "EXAM_BUILT_IN_V1";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GraduationSnapshotFactory snapshotFactory;

    public ExamCertificateTemplate(GraduationSnapshotFactory snapshotFactory) {
        this.snapshotFactory = snapshotFactory;
    }

    public CertificateSnapshot snapshotOf(Student student, String subjectName, LocalDate examDate, LocalDate issuedOn) {
        CertificateSnapshot base = snapshotFactory.create(student, "examen de", subjectName, null, null, examDate);
        return new CertificateSnapshot(base.holderFullName(), base.documentTypeLabel(), base.documentNumber(), base.titleKind(),
                base.titleName(), base.programLevel(), base.hours(), base.graduationDate(), issuedOn,
                base.institutionName(), base.signatories(), EXAM_BUILT_IN_V1);
    }

    public CertificateTemplateDefinition definitionFor(CertificateSnapshot snapshot, LocalDate validUntil,
                                                       RecipientType recipientType, String recipientText) {
        String body = "Se certifica que {holder}, {documentType} {documentNumber}, rindió examen de {titleName} de "
                + escape(snapshot.institutionName()) + " el {graduationDate}." + recipientSentence(recipientType, recipientText)
                + " Válido hasta " + validUntil.format(DATE_FORMAT) + ".";
        return new CertificateTemplateDefinition(EXAM_BUILT_IN_V1, PageFormat.A4_LANDSCAPE, "CONSTANCIA DE EXAMEN RENDIDO", body,
                new Rect(100, 150, 642, 280), new Rect(690, 60, 90, 90), new Rect(430, 60, 250, 40), new Rect(100, 60, 260, 80));
    }

    private static String recipientSentence(RecipientType type, String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String kind = type == null ? "" : switch (type) {
            case HEALTH_INSURANCE -> "obra social: ";
            case EMPLOYER -> "empleador: ";
            case STUDENT_TRANSPORT -> "transporte estudiantil: ";
            case OTHER -> "";
        };
        return " Se extiende para presentar ante " + kind + escape(text) + ".";
    }

    private static String escape(String text) {
        return text.replace('{', '(').replace('}', ')');
    }
}
