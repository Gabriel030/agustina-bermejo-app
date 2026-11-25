package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateIssuer;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularEnrollmentFactsFinder;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionNotFoundException;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocument;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionScope;
import com.red_escuela.backend.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;

@Component
public class RegularDebtExceptionDocumentIssuer implements DebtExceptionDocumentIssuer {

    private final RegularCertificateIssuer issuer;
    private final RegularCertificatePdfProvider pdfProvider;
    private final RegularEnrollmentFactsFinder factsFinder;

    public RegularDebtExceptionDocumentIssuer(RegularCertificateIssuer issuer, RegularCertificatePdfProvider pdfProvider,
                                              RegularEnrollmentFactsFinder factsFinder) {
        this.issuer = issuer;
        this.pdfProvider = pdfProvider;
        this.factsFinder = factsFinder;
    }

    @Override
    public boolean supports(CertificateDocumentType type) {
        return type == CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE;
    }

    @Override
    public void requireScopeBelongsToStudent(DebtExceptionScope scope) {
        if (scope.enrollmentId() == null) {
            throw new DebtExceptionNotFoundException();
        }
        try {
            factsFinder.findOwnedBy(RegularityKind.PROGRAM, scope.enrollmentId(), scope.studentId());
        } catch (NotFoundException notOwned) {
            throw new DebtExceptionNotFoundException();
        }
    }

    @Override
    public DebtExceptionDocument render(DebtExceptionScope scope) {
        requireScopeBelongsToStudent(scope);
        RegularCertificateResponse issued = issuer.issueUnderDebtException(scope.studentId(), scope.enrollmentId());
        RegularCertificatePdf pdf = pdfProvider.pdfOf(issued.id());
        return new DebtExceptionDocument(pdf.code(), pdf.content(), "documentId=" + issued.id() + ";code=" + pdf.code());
    }
}
