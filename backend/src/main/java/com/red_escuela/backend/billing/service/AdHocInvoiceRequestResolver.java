package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.exception.InvalidAdHocFeeTypeException;
import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.exception.SubjectNotFoundException;
import com.red_escuela.backend.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.repository.SubjectRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class AdHocInvoiceRequestResolver {

    private static final Set<FeeType> AD_HOC_FEE_TYPES = EnumSet.of(
            FeeType.EXAM_RIGHT, FeeType.RETAKEN_SUBJECT_FEE, FeeType.DEGREE_ISSUANCE, FeeType.ADMINISTRATIVE_FEE);

    private final SubjectRepository subjectRepository;
    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;

    public AdHocInvoiceRequestResolver(SubjectRepository subjectRepository, ExamBoardRegistrationRepository examBoardRegistrationRepository) {
        this.subjectRepository = subjectRepository;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
    }

    public void validateFeeType(FeeType feeType) {
        if (!AD_HOC_FEE_TYPES.contains(feeType)) {
            throw new InvalidAdHocFeeTypeException(feeType);
        }
    }

    public Subject resolveSubject(Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        return subjectRepository.findById(subjectId).orElseThrow(() -> new SubjectNotFoundException(subjectId));
    }

    public ExamBoardRegistration resolveExamBoardRegistration(Long examBoardRegistrationId) {
        if (examBoardRegistrationId == null) {
            return null;
        }
        return examBoardRegistrationRepository.findById(examBoardRegistrationId)
                .orElseThrow(() -> new ExamBoardRegistrationNotFoundException(examBoardRegistrationId));
    }
}
