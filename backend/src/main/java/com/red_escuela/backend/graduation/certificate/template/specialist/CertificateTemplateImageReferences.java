package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateImagePurpose;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateImageNotFoundException;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateImageRepository;
import org.springframework.stereotype.Component;

@Component
public class CertificateTemplateImageReferences {

    private final CertificateTemplateImageRepository imageRepository;

    public CertificateTemplateImageReferences(CertificateTemplateImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    public void requireExisting(Long templateId, CertificateTemplateContent content) {
        require(templateId, content.logoImageId(), TemplateImagePurpose.LOGO);
        if (content.firstSigner() != null) {
            require(templateId, content.firstSigner().signatureImageId(), TemplateImagePurpose.SIGNATURE);
        }
        if (content.secondSigner() != null) {
            require(templateId, content.secondSigner().signatureImageId(), TemplateImagePurpose.SIGNATURE);
        }
    }

    private void require(Long templateId, Long imageId, TemplateImagePurpose purpose) {
        if (imageId == null) {
            return;
        }
        boolean valid = imageRepository.findByIdAndTemplateId(imageId, templateId)
                .filter(image -> image.getPurpose() == purpose).isPresent();
        if (!valid) {
            throw new TemplateImageNotFoundException();
        }
    }
}
