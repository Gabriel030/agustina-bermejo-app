package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSubmissionResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.SubmitRegularCertificateRequest;

public interface SubmitRegularCertificateUseCase {

    RegularCertificateSubmissionResponse submit(SubmitRegularCertificateRequest request);
}
