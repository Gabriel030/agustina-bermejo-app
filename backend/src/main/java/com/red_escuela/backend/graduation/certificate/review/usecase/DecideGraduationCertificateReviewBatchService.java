package com.red_escuela.backend.graduation.certificate.review.usecase;

import com.red_escuela.backend.graduation.certificate.review.dto.CertificateReviewBatchOutcome;
import com.red_escuela.backend.graduation.certificate.review.dto.DecideGraduationCertificateReviewBatchRequest;
import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewBatchItemResponse;
import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewBatchResponse;
import com.red_escuela.backend.graduation.certificate.review.specialist.GraduationCertificateReviewDecider;
import com.red_escuela.backend.graduation.certificate.review.specialist.GraduationCertificateReviewReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;
import com.red_escuela.backend.shared.exception.UnprocessableEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;

@UseCase
public class DecideGraduationCertificateReviewBatchService implements DecideGraduationCertificateReviewBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(DecideGraduationCertificateReviewBatchService.class);
    private static final String UNEXPECTED_ERROR_CODE = "INTERNAL_ERROR";

    private final GraduationCertificateReviewDecider decider;
    private final GraduationCertificateReviewReader reader;

    public DecideGraduationCertificateReviewBatchService(GraduationCertificateReviewDecider decider,
                                                         GraduationCertificateReviewReader reader) {
        this.decider = decider;
        this.reader = reader;
    }

    @Override
    public GraduationCertificateReviewBatchResponse decideBatch(DecideGraduationCertificateReviewBatchRequest request) {
        decider.requireValidReason(request.decision(), request.reason());
        List<GraduationCertificateReviewBatchItemResponse> results = new ArrayList<>();
        for (Long graduationRequestId : request.graduationRequestIds().stream().distinct().toList()) {
            results.add(decideItem(graduationRequestId, request));
        }
        int decided = (int) results.stream().filter(result -> result.outcome() == CertificateReviewBatchOutcome.DECIDED).count();
        return new GraduationCertificateReviewBatchResponse(results.size(), decided, results.size() - decided, results);
    }

    private GraduationCertificateReviewBatchItemResponse decideItem(Long graduationRequestId,
                                                                     DecideGraduationCertificateReviewBatchRequest request) {
        try {
            decider.decide(graduationRequestId, request.decision(), request.reason());
        } catch (RuntimeException e) {
            log.warn("Falló la revisión en lote del trámite de egreso {}: {}", graduationRequestId, e.getMessage());
            return new GraduationCertificateReviewBatchItemResponse(graduationRequestId, CertificateReviewBatchOutcome.FAILED, null,
                    errorCodeOf(e));
        }
        return new GraduationCertificateReviewBatchItemResponse(graduationRequestId, CertificateReviewBatchOutcome.DECIDED,
                reader.get(graduationRequestId), null);
    }

    private String errorCodeOf(RuntimeException e) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            return ErrorCodeResponse.CONCURRENT_MODIFICATION_CODE;
        }
        boolean domainError = e instanceof ConflictException || e instanceof NotFoundException
                || e instanceof UnprocessableEntityException || e instanceof ReportsErrorCode;
        return domainError ? ErrorCodeResponse.codeOf(e.getClass()) : UNEXPECTED_ERROR_CODE;
    }
}
