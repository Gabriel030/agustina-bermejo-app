package com.red_escuela.backend.graduation.certificate.template.entity;

import com.red_escuela.backend.graduation.certificate.template.dto.TemplateImagePurpose;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "certificate_template_image")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateTemplateImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20, updatable = false)
    private TemplateImagePurpose purpose;

    @Column(name = "content_type", nullable = false, length = 40, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "width_pixels", nullable = false, updatable = false)
    private int widthPixels;

    @Column(name = "height_pixels", nullable = false, updatable = false)
    private int heightPixels;

    @Column(name = "sha256", nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by_id", updatable = false)
    private Long uploadedById;
}
