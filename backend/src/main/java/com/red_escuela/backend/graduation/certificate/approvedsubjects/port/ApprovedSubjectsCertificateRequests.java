package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;

public interface ApprovedSubjectsCertificateRequests {

    ApprovedSubjectsCertificateResponse request(RequestApprovedSubjectsCertificateRequest request);
}
