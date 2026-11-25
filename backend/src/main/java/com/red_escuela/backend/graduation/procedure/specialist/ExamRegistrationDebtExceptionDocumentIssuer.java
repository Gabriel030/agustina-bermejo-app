package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.evaluation.api.OwnExamRegistrationFinder;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionNotFoundException;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocument;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionScope;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class ExamRegistrationDebtExceptionDocumentIssuer implements DebtExceptionDocumentIssuer {

    private final ExamRegistrationCertificateIssuer issuer;
    private final ExamRegistrationCertificatePdfProvider pdfProvider;
    private final OwnExamRegistrationFinder registrationFinder;
    private final Clock clock;

    public ExamRegistrationDebtExceptionDocumentIssuer(ExamRegistrationCertificateIssuer issuer,
                                                       ExamRegistrationCertificatePdfProvider pdfProvider,
                                                       OwnExamRegistrationFinder registrationFinder, Clock clock) {
        this.issuer = issuer;
        this.pdfProvider = pdfProvider;
        this.registrationFinder = registrationFinder;
        this.clock = clock;
    }

    @Override
    public boolean supports(CertificateDocumentType type) {
        return type == CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE;
    }

    @Override
    public void requireScopeBelongsToStudent(DebtExceptionScope scope) {
        if (scope.registrationId() == null || registrationFinder.findActiveOwned(scope.studentId(), scope.registrationId(),
                LocalDateTime.now(clock).toLocalDate().atStartOfDay()).isEmpty()) {
            throw new DebtExceptionNotFoundException();
        }
    }

    @Override
    public DebtExceptionDocument render(DebtExceptionScope scope) {
        requireScopeBelongsToStudent(scope);
        ExamRegistrationCertificate issued = issuer.issueUnderDebtException(scope.studentId(), scope.registrationId());
        ProcedureDocumentPdf pdf = pdfProvider.pdfOfOwn(issued.getId(), scope.studentId());
        return new DebtExceptionDocument(pdf.code(), pdf.content(), "documentId=" + issued.getId() + ";code=" + pdf.code());
    }
}
