package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateResponse;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class ExamCertificateResponses {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CertificateVerificationUrl verificationUrl;

    public ExamCertificateResponses(CertificateVerificationUrl verificationUrl) {
        this.verificationUrl = verificationUrl;
    }

    public ExamCertificateResponse of(ExamCertificate certificate) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        return new ExamCertificateResponse(certificate.getId(), certificate.getCode(), certificate.getRegistrationId(),
                certificate.getExamBoardId(), certificate.getStudentId(), snapshot.holderFullName(), snapshot.titleName(),
                snapshot.graduationDate(), snapshot.titleName() + " - " + snapshot.graduationDate().format(DATE_FORMAT),
                certificate.getRecipientType(), certificate.getRecipientText(), certificate.getStatus(), certificate.getOrigin(),
                certificate.getIssuedAt(), certificate.getExpiresAt(), certificate.getRegistryNumber(),
                verificationUrl.of(certificate.certificateCode()));
    }
}
