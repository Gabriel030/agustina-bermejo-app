package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.BatchItemOutcome;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateBatchItemResponse;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateBatchRequest;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateCatalog;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateIssuer;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;
import com.red_escuela.backend.shared.exception.UnprocessableEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@UseCase
public class IssueGraduationCertificateBatchService implements IssueGraduationCertificateBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(IssueGraduationCertificateBatchService.class);
    private static final String UNEXPECTED_ERROR_CODE = "INTERNAL_ERROR";

    private final GraduationCertificateIssuer issuer;
    private final GraduationCertificateCatalog catalog;

    public IssueGraduationCertificateBatchService(GraduationCertificateIssuer issuer, GraduationCertificateCatalog catalog) {
        this.issuer = issuer;
        this.catalog = catalog;
    }

    @Override
    public GraduationCertificateBatchResponse issueBatch(IssueGraduationCertificateBatchRequest request) {
        List<GraduationCertificateBatchItemResponse> results = new ArrayList<>();
        for (IssueGraduationCertificateRequest item : request.items()) {
            results.add(issueItem(item));
        }
        int issued = (int) results.stream().filter(result -> result.outcome() == BatchItemOutcome.ISSUED).count();
        return new GraduationCertificateBatchResponse(results.size(), issued, results.size() - issued, results);
    }

    private GraduationCertificateBatchItemResponse issueItem(IssueGraduationCertificateRequest item) {
        try {
            GraduationCertificateResponse issued = issuer.issue(item.kind(), item.enrollmentId());
            return new GraduationCertificateBatchItemResponse(item.enrollmentId(), item.kind(), BatchItemOutcome.ISSUED,
                    catalog.summaryOf(issued.id()), null);
        } catch (RuntimeException e) {
            log.warn("Falló la emisión en lote de la inscripción {} ({}): {}", item.enrollmentId(), item.kind(), e.getMessage());
            return new GraduationCertificateBatchItemResponse(item.enrollmentId(), item.kind(), BatchItemOutcome.FAILED, null,
                    errorCodeOf(e));
        }
    }

    private String errorCodeOf(RuntimeException e) {
        boolean domainError = e instanceof ConflictException || e instanceof NotFoundException
                || e instanceof UnprocessableEntityException || e instanceof ReportsErrorCode;
        return domainError ? ErrorCodeResponse.codeOf(e.getClass()) : UNEXPECTED_ERROR_CODE;
    }
}
