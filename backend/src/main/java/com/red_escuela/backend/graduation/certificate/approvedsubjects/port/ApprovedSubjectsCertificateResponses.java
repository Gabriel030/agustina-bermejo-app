package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;

public interface ApprovedSubjectsCertificateResponses {

    ApprovedSubjectsCertificateResponse of(ApprovedSubjectsCertificate certificate);
}
