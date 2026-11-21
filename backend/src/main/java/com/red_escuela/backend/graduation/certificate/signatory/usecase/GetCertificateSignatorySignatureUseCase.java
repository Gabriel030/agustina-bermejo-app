package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureContent;

public interface GetCertificateSignatorySignatureUseCase {

    SignatorySignatureContent get(Long id);
}
