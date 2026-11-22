package com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;

public interface RequestApprovedSubjectsCertificateUseCase {

    ApprovedSubjectsCertificateResponse request(RequestApprovedSubjectsCertificateRequest request);
}
