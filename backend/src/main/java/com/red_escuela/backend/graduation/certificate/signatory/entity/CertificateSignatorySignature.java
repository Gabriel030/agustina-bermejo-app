package com.red_escuela.backend.graduation.certificate.signatory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "certificate_signatory_signature")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateSignatorySignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signatory_id", nullable = false, updatable = false)
    private Long signatoryId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width_pixels", nullable = false)
    private int widthPixels;

    @Column(name = "height_pixels", nullable = false)
    private int heightPixels;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by_id")
    private Long uploadedById;
}
