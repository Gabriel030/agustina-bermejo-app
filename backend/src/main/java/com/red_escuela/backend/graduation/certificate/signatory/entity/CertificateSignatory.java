package com.red_escuela.backend.graduation.certificate.signatory.entity;

import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "certificate_signatory")
@Getter
@NoArgsConstructor
public class CertificateSignatory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "role", nullable = false, length = 80)
    private String role;

    @Column(name = "active", nullable = false)
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "certificate_signatory_permission", joinColumns = @JoinColumn(name = "signatory_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private Set<SignatoryDocumentType> allowedDocumentTypes = EnumSet.noneOf(SignatoryDocumentType.class);

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CertificateSignatory create(String name, String role, Set<SignatoryDocumentType> types, LocalDateTime now) {
        CertificateSignatory signatory = new CertificateSignatory();
        signatory.name = name;
        signatory.role = role;
        signatory.active = true;
        signatory.allowedDocumentTypes = copyOf(types);
        signatory.createdAt = now;
        signatory.updatedAt = now;
        return signatory;
    }

    public void edit(String name, String role, Set<SignatoryDocumentType> types, LocalDateTime now) {
        this.name = name;
        this.role = role;
        this.allowedDocumentTypes.clear();
        this.allowedDocumentTypes.addAll(types);
        this.updatedAt = now;
    }

    public void changeActive(boolean active, LocalDateTime now) {
        this.active = active;
        this.updatedAt = now;
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }

    public boolean allows(SignatoryDocumentType type) {
        return allowedDocumentTypes.contains(type);
    }

    private static Set<SignatoryDocumentType> copyOf(Set<SignatoryDocumentType> types) {
        Set<SignatoryDocumentType> copy = EnumSet.noneOf(SignatoryDocumentType.class);
        copy.addAll(types);
        return copy;
    }
}
