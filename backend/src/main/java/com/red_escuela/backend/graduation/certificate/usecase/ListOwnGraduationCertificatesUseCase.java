package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.OwnGraduationCertificateResponse;
import java.util.List;

public interface ListOwnGraduationCertificatesUseCase {

    List<OwnGraduationCertificateResponse> listOwn();
}
