package com.red_escuela.backend.graduation.certificate.signatory.specialist;

import com.red_escuela.backend.graduation.certificate.signatory.dto.ResolvedSignatory;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatory;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatorySignature;
import com.red_escuela.backend.graduation.certificate.signatory.exception.SignatoryDuplicatedInTemplateException;
import com.red_escuela.backend.graduation.certificate.signatory.exception.SignatoryInactiveException;
import com.red_escuela.backend.graduation.certificate.signatory.exception.SignatoryNotAllowedForDocumentTypeException;
import com.red_escuela.backend.graduation.certificate.signatory.repository.CertificateSignatorySignatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CertificateSignatoryResolver {

    private final CertificateSignatoryFinder finder;
    private final CertificateSignatorySignatureRepository signatureRepository;

    public CertificateSignatoryResolver(CertificateSignatoryFinder finder, CertificateSignatorySignatureRepository signatureRepository) {
        this.finder = finder;
        this.signatureRepository = signatureRepository;
    }

    public void requireDistinct(Long first, Long second) {
        if (first != null && first.equals(second)) {
            throw new SignatoryDuplicatedInTemplateException();
        }
    }

    public ResolvedSignatory selectFor(Long id, SignatoryDocumentType documentType) {
        if (id == null) {
            return null;
        }
        CertificateSignatory signatory = finder.find(id);
        if (!signatory.isActive()) {
            throw new SignatoryInactiveException();
        }
        if (!signatory.allows(documentType)) {
            throw new SignatoryNotAllowedForDocumentTypeException();
        }
        CertificateSignatorySignature signature = signatureRepository.findBySignatoryId(id).orElse(null);
        return new ResolvedSignatory(signatory.getId(), signatory.getName(), signatory.getRole(), signature == null
                ? null
                : new ResolvedSignatory.SignatureImage(signature.getContentType(), signature.getSizeBytes(), signature.getWidthPixels(),
                signature.getHeightPixels(), signature.getSha256(), signature.getContent()));
    }
}
