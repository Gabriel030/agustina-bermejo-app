package com.red_escuela.backend.graduation.certificate.signatory.specialist;

import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatory;
import com.red_escuela.backend.graduation.certificate.signatory.exception.SignatoryNotFoundException;
import com.red_escuela.backend.graduation.certificate.signatory.repository.CertificateSignatoryRepository;
import org.springframework.stereotype.Component;

@Component
public class CertificateSignatoryFinder {

    private final CertificateSignatoryRepository repository;

    public CertificateSignatoryFinder(CertificateSignatoryRepository repository) {
        this.repository = repository;
    }

    public CertificateSignatory find(Long id) {
        return repository.findById(id).orElseThrow(SignatoryNotFoundException::new);
    }
}
