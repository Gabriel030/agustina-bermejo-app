package com.red_escuela.backend.graduation.certificate.review.usecase;

import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewResponse;
import com.red_escuela.backend.graduation.certificate.review.specialist.GraduationCertificateReviewReader;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@UseCase
public class ListGraduationCertificateReviewsService implements ListGraduationCertificateReviewsUseCase {

    private final GraduationCertificateReviewReader reader;

    public ListGraduationCertificateReviewsService(GraduationCertificateReviewReader reader) {
        this.reader = reader;
    }

    @Override
    public Page<GraduationCertificateReviewResponse> list(CertificateReviewStatus status, String query, Pageable pageable) {
        return reader.list(status, query, pageable);
    }
}
