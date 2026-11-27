package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificatePdf;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnApprovedSubjectsEnrollments;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsCertificateIssuer;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionNotFoundException;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocument;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionScope;
import org.springframework.stereotype.Component;

@Component
public class ApprovedSubjectsDebtExceptionDocumentIssuer implements DebtExceptionDocumentIssuer {

    private final ApprovedSubjectsCertificateIssuer issuer;
    private final ApprovedSubjectsCertificatePdfProvider pdfProvider;
    private final OwnApprovedSubjectsEnrollments ownEnrollments;

    public ApprovedSubjectsDebtExceptionDocumentIssuer(ApprovedSubjectsCertificateIssuer issuer,
                                                       ApprovedSubjectsCertificatePdfProvider pdfProvider,
                                                       OwnApprovedSubjectsEnrollments ownEnrollments) {
        this.issuer = issuer;
        this.pdfProvider = pdfProvider;
        this.ownEnrollments = ownEnrollments;
    }

    @Override
    public boolean supports(CertificateDocumentType type) {
        return type == CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE;
    }

    @Override
    public void requireScopeBelongsToStudent(DebtExceptionScope scope) {
        if (scope.enrollmentId() == null || ownEnrollments.findOwnedBy(scope.studentId(), scope.enrollmentId()).isEmpty()) {
            throw new DebtExceptionNotFoundException();
        }
    }

    @Override
    public DebtExceptionDocument render(DebtExceptionScope scope) {
        requireScopeBelongsToStudent(scope);
        ApprovedSubjectsCertificateResponse issued = issuer.issueUnderDebtException(scope.studentId(), scope.enrollmentId());
        ApprovedSubjectsCertificatePdf pdf = pdfProvider.pdfOfOwn(issued.id(), scope.studentId());
        return new DebtExceptionDocument(pdf.code(), pdf.content(), "documentId=" + issued.id() + ";code=" + pdf.code());
    }
}
