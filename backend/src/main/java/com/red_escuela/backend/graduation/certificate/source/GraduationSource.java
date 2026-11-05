package com.red_escuela.backend.graduation.certificate.source;

import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;

public interface GraduationSource {

    GraduationCertificateKind kind();

    void requireEligible(Long enrollmentId);

    CertificateSnapshot snapshotOf(Long enrollmentId);

    Long studentIdOf(Long enrollmentId);

    Long titleIdOf(Long enrollmentId);
}
