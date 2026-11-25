package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateStore;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class ApprovedSubjectsCertificateRepositoryStore implements ApprovedSubjectsCertificateStore {

    private final ApprovedSubjectsCertificateRepository repository;

    public ApprovedSubjectsCertificateRepositoryStore(ApprovedSubjectsCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ApprovedSubjectsCertificate> findById(Long id) {
        return repository.findByIdForUpdate(id);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovedSubjectsCertificate save(ApprovedSubjectsCertificate certificate) {
        return repository.saveAndFlush(certificate);
    }
}
