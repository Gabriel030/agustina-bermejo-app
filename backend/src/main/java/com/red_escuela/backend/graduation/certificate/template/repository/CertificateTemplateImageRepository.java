package com.red_escuela.backend.graduation.certificate.template.repository;

import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateTemplateImageRepository extends JpaRepository<CertificateTemplateImage, Long> {

    Optional<CertificateTemplateImage> findByIdAndTemplateId(Long id, Long templateId);
}
