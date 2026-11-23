package com.red_escuela.backend.graduation.certificate.template.entity;

import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "certificate_template")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, updatable = false)
    private GraduationCertificateKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 40, updatable = false)
    private DocumentTemplateType documentType;
}
