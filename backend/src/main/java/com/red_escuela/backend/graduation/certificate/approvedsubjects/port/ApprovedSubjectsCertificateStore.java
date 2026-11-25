package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;

import java.util.Optional;

public interface ApprovedSubjectsCertificateStore {

    Optional<ApprovedSubjectsCertificate> findById(Long id);

    ApprovedSubjectsCertificate save(ApprovedSubjectsCertificate certificate);
}
