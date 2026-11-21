package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.OwnGraduationCertificateResponse;
import java.util.List;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListOwnGraduationCertificatesService implements ListOwnGraduationCertificatesUseCase {

    @Override
    public List<OwnGraduationCertificateResponse> listOwn() {
        throw new GraduationCertificateOperationPendingException();
    }
}
