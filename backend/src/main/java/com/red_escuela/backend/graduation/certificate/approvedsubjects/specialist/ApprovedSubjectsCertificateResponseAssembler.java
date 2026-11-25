package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateResponses;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

@Component
public class ApprovedSubjectsCertificateResponseAssembler implements ApprovedSubjectsCertificateResponses {

    private final CertificateVerificationUrl verificationUrl;

    public ApprovedSubjectsCertificateResponseAssembler(CertificateVerificationUrl verificationUrl) {
        this.verificationUrl = verificationUrl;
    }

    @Override
    public ApprovedSubjectsCertificateResponse of(ApprovedSubjectsCertificate certificate) {
        ApprovedSubjectsSnapshot snapshot = certificate.getSnapshot();
        return new ApprovedSubjectsCertificateResponse(certificate.getId(), certificate.getCode(), certificate.getStudentId(),
                snapshot.holderFullName(), snapshot.programName(), certificate.getProgramEnrollmentId(),
                DocumentValidityStatus.valueOf(certificate.getStatus().name()), certificate.getIssuedAt(), certificate.getExpiresAt(),
                Grade.ofNullable(snapshot.averageGrade()), snapshot.approvedCount(), snapshot.totalSubjects(),
                snapshot.progressPercentage().setScale(0, RoundingMode.HALF_UP).intValue(),
                verificationUrl.of(certificate.certificateCode()));
    }
}
