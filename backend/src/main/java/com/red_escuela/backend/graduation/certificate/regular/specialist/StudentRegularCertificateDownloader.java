package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExpiredException;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateRevokedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class StudentRegularCertificateDownloader {

    private final RegularStudentCertificateRepository repository;
    private final RegularCertificatePdfProvider pdfProvider;
    private final Clock clock;

    public StudentRegularCertificateDownloader(RegularStudentCertificateRepository repository, RegularCertificatePdfProvider pdfProvider,
                                               Clock clock) {
        this.repository = repository;
        this.pdfProvider = pdfProvider;
        this.clock = clock;
    }

    @Transactional
    public RegularCertificatePdf pdfOf(Long certificateId, Long studentId) {
        RegularStudentCertificate certificate = repository.findByIdAndStudentId(certificateId, studentId)
                .orElseThrow(() -> new RegularCertificateNotFoundException());
        if (certificate.getStatus() == RegularCertificateStatus.REVOKED) {
            throw new CertificateRevokedException();
        }
        if (certificate.isExpiredAt(LocalDateTime.now(clock))) {
            throw new CertificateExpiredException();
        }
        return pdfProvider.pdfOf(certificateId);
    }
}
