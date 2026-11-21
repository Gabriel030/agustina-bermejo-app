package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureContent;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateSignatorySignatureService implements GetCertificateSignatorySignatureUseCase {

    @Override
    public SignatorySignatureContent get(Long id) {
        throw new GraduationCertificateOperationPendingException();
    }
}
