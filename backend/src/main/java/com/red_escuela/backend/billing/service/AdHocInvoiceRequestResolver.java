package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.collections.service.ExamFeeOverdueDebtGuard;
import com.red_escuela.backend.billing.exception.InvalidAdHocFeeTypeException;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationFinder;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationView;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class AdHocInvoiceRequestResolver {

    private static final Set<FeeType> AD_HOC_FEE_TYPES = EnumSet.of(
            FeeType.EXAM_FEE, FeeType.RETAKEN_SUBJECT_FEE, FeeType.DEGREE_ISSUANCE_FEE, FeeType.ADMINISTRATIVE_FEE);

    private final AcademicReferenceFinder academicReferenceFinder;
    private final ExamBoardRegistrationFinder examBoardRegistrationFinder;
    private final ExamFeeOverdueDebtGuard examFeeDebtGuard;

    public AdHocInvoiceRequestResolver(AcademicReferenceFinder academicReferenceFinder, ExamBoardRegistrationFinder examBoardRegistrationFinder,
                                     ExamFeeOverdueDebtGuard examFeeDebtGuard) {
        this.academicReferenceFinder = academicReferenceFinder;
        this.examBoardRegistrationFinder = examBoardRegistrationFinder;
        this.examFeeDebtGuard = examFeeDebtGuard;
    }

    public void validateFeeType(FeeType feeType) {
        if (!AD_HOC_FEE_TYPES.contains(feeType)) {
            throw new InvalidAdHocFeeTypeException(feeType);
        }
    }

    public void requireExamFeeAllowed(ProgramEnrollment enrollment, FeeType feeType) {
        examFeeDebtGuard.verifyExamFeeMayBeIssued(enrollment.getStudent().getId(), feeType);
    }

    public void requireExamFeeAllowed(CourseEnrollment enrollment, FeeType feeType) {
        examFeeDebtGuard.verifyExamFeeMayBeIssued(enrollment.getStudent().getId(), feeType);
    }

    public Subject resolveSubject(Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        return academicReferenceFinder.findSubject(subjectId);
    }

    public ExamBoardRegistrationView resolveExamBoardRegistration(Long examBoardRegistrationId) {
        if (examBoardRegistrationId == null) {
            return null;
        }
        return examBoardRegistrationFinder.requireById(examBoardRegistrationId);
    }
}
