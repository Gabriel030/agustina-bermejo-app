package com.red_escuela.backend.graduation.certificate.review.usecase;

import com.red_escuela.backend.graduation.certificate.review.dto.CertificateReviewDecision;
import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewResponse;
import com.red_escuela.backend.graduation.certificate.review.specialist.GraduationCertificateReviewDecider;
import com.red_escuela.backend.graduation.certificate.review.specialist.GraduationCertificateReviewReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class EnableGraduationCertificateService implements EnableGraduationCertificateUseCase {

    private final GraduationCertificateReviewDecider decider;
    private final GraduationCertificateReviewReader reader;

    public EnableGraduationCertificateService(GraduationCertificateReviewDecider decider, GraduationCertificateReviewReader reader) {
        this.decider = decider;
        this.reader = reader;
    }

    @Override
    public GraduationCertificateReviewResponse enable(Long graduationRequestId) {
        return reader.get(decider.decide(graduationRequestId, CertificateReviewDecision.ENABLE, null));
    }
}
