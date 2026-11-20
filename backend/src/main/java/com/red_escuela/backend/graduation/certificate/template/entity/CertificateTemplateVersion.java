package com.red_escuela.backend.graduation.certificate.template.entity;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateSigner;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
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
@Table(name = "certificate_template_version")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateTemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TemplateVersionStatus status;

    @Column(name = "based_on_version_number")
    private Integer basedOnVersionNumber;

    @Column(name = "institute_name", length = 120)
    private String instituteName;

    @Column(name = "logo_image_id")
    private Long logoImageId;

    @Column(name = "title", length = 80)
    private String title;

    @Column(name = "intro_text", length = 160)
    private String introText;

    @Column(name = "approval_text", length = 160)
    private String approvalText;

    @Column(name = "dictation_text", length = 160)
    private String dictationText;

    @Column(name = "qr_label", length = 160)
    private String qrLabel;

    @Column(name = "footer_legend", length = 240)
    private String footerLegend;

    @Column(name = "first_signer_name", length = 80)
    private String firstSignerName;

    @Column(name = "first_signer_role", length = 80)
    private String firstSignerRole;

    @Column(name = "first_signer_signature_image_id")
    private Long firstSignerSignatureImageId;

    @Column(name = "second_signer_name", length = 80)
    private String secondSignerName;

    @Column(name = "second_signer_role", length = 80)
    private String secondSignerRole;

    @Column(name = "second_signer_signature_image_id")
    private Long secondSignerSignatureImageId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "published_by_id")
    private Long publishedById;

    @Column(name = "published_by_name", length = 160)
    private String publishedByName;

    public static CertificateTemplateVersion draftOf(Long templateId, Integer basedOnVersionNumber, CertificateTemplateContent content,
                                                     LocalDateTime now) {
        CertificateTemplateVersion draft = CertificateTemplateVersion.builder()
                .templateId(templateId)
                .status(TemplateVersionStatus.DRAFT)
                .basedOnVersionNumber(basedOnVersionNumber)
                .build();
        draft.replaceContent(content, now);
        return draft;
    }

    public void replaceContent(CertificateTemplateContent content, LocalDateTime now) {
        this.instituteName = content.instituteName();
        this.logoImageId = content.logoImageId();
        this.title = content.title();
        this.introText = content.introText();
        this.approvalText = content.approvalText();
        this.dictationText = content.dictationText();
        this.qrLabel = content.qrLabel();
        this.footerLegend = content.footerLegend();
        this.firstSignerName = content.firstSigner() == null ? null : content.firstSigner().name();
        this.firstSignerRole = content.firstSigner() == null ? null : content.firstSigner().role();
        this.firstSignerSignatureImageId = content.firstSigner() == null ? null : content.firstSigner().signatureImageId();
        this.secondSignerName = content.secondSigner() == null ? null : content.secondSigner().name();
        this.secondSignerRole = content.secondSigner() == null ? null : content.secondSigner().role();
        this.secondSignerSignatureImageId = content.secondSigner() == null ? null : content.secondSigner().signatureImageId();
        this.updatedAt = now;
    }

    public CertificateTemplateContent content() {
        return new CertificateTemplateContent(instituteName, logoImageId, title, introText, approvalText, dictationText, qrLabel,
                footerLegend, signer(firstSignerName, firstSignerRole, firstSignerSignatureImageId),
                signer(secondSignerName, secondSignerRole, secondSignerSignatureImageId));
    }

    public void publishAs(int number, Long actorId, String actorName, LocalDateTime now) {
        this.status = TemplateVersionStatus.PUBLISHED;
        this.versionNumber = number;
        this.publishedAt = now;
        this.publishedById = actorId;
        this.publishedByName = actorName;
        this.updatedAt = now;
    }

    public boolean isDraft() {
        return status == TemplateVersionStatus.DRAFT;
    }

    private static CertificateTemplateSigner signer(String name, String role, Long signatureImageId) {
        return name == null && role == null && signatureImageId == null ? null : new CertificateTemplateSigner(name, role, signatureImageId);
    }
}
