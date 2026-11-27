package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationFinder;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateIssuer;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificatePdfProvider;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionNotFoundException;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocument;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionScope;
import com.red_escuela.backend.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;

@Component
public class ExamDebtExceptionDocumentIssuer implements DebtExceptionDocumentIssuer {

    private final ExamCertificateIssuer issuer;
    private final ExamCertificatePdfProvider pdfProvider;
    private final ExamBoardRegistrationFinder registrationFinder;
    private final ExamBoardAttendanceReader boardReader;

    public ExamDebtExceptionDocumentIssuer(ExamCertificateIssuer issuer, ExamCertificatePdfProvider pdfProvider,
                                           ExamBoardRegistrationFinder registrationFinder, ExamBoardAttendanceReader boardReader) {
        this.issuer = issuer;
        this.pdfProvider = pdfProvider;
        this.registrationFinder = registrationFinder;
        this.boardReader = boardReader;
    }

    @Override
    public boolean supports(CertificateDocumentType type) {
        return type == CertificateDocumentType.EXAM_CERTIFICATE;
    }

    @Override
    public void requireScopeBelongsToStudent(DebtExceptionScope scope) {
        if (scope.registrationId() == null || !registrationBelongsToStudent(scope)) {
            throw new DebtExceptionNotFoundException();
        }
    }

    private boolean registrationBelongsToStudent(DebtExceptionScope scope) {
        try {
            Long examBoardId = registrationFinder.requireById(scope.registrationId()).examBoardId();
            return boardReader.attendanceOf(examBoardId).attendees().stream()
                    .anyMatch(attendee -> attendee.registrationId().equals(scope.registrationId())
                            && attendee.studentId().equals(scope.studentId()));
        } catch (NotFoundException notFound) {
            return false;
        }
    }

    @Override
    public DebtExceptionDocument render(DebtExceptionScope scope) {
        requireScopeBelongsToStudent(scope);
        ExamCertificate issued = issuer.issueUnderDebtException(scope.studentId(), scope.registrationId());
        GraduationCertificatePdf pdf = pdfProvider.pdfOf(issued.getId());
        return new DebtExceptionDocument(pdf.code(), pdf.content(), "documentId=" + issued.getId() + ";code=" + pdf.code());
    }
}
