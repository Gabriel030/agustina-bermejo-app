package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.exception.InvalidAdHocFeeTypeException;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SubjectNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.SubjectRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class AdHocInvoiceService {

    private static final Set<FeeType> AD_HOC_FEE_TYPES = EnumSet.of(
            FeeType.EXAM_RIGHT, FeeType.RETAKEN_SUBJECT_FEE, FeeType.DEGREE_ISSUANCE, FeeType.ADMINISTRATIVE_FEE);

    private final InvoiceFactory invoiceFactory;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;

    public AdHocInvoiceService(InvoiceFactory invoiceFactory, CourseEnrollmentRepository courseEnrollmentRepository,
                                ProgramEnrollmentRepository programEnrollmentRepository, SubjectRepository subjectRepository,
                                ExamBoardRegistrationRepository examBoardRegistrationRepository) {
        this.invoiceFactory = invoiceFactory;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectRepository = subjectRepository;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
    }

    public Invoice createForCourseEnrollment(Long courseEnrollmentId, CreateAdHocInvoiceRequest request) {
        validateFeeType(request.feeType());
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        Subject subject = resolveSubject(request.subjectId());
        ExamBoardRegistration examBoardRegistration = resolveExamBoardRegistration(request.examBoardRegistrationId());
        return invoiceFactory.createAdHocFeeForCourseEnrollment(
                enrollment, request.feeType(), request.amount(), request.dueDate(), request.description(), subject, examBoardRegistration);
    }

    public Invoice createForProgramEnrollment(Long programEnrollmentId, CreateAdHocInvoiceRequest request) {
        validateFeeType(request.feeType());
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        Subject subject = resolveSubject(request.subjectId());
        ExamBoardRegistration examBoardRegistration = resolveExamBoardRegistration(request.examBoardRegistrationId());
        return invoiceFactory.createAdHocFeeForProgramEnrollment(
                enrollment, request.feeType(), request.amount(), request.dueDate(), request.description(), subject, examBoardRegistration);
    }

    private void validateFeeType(FeeType feeType) {
        if (!AD_HOC_FEE_TYPES.contains(feeType)) {
            throw new InvalidAdHocFeeTypeException(feeType);
        }
    }

    private Subject resolveSubject(Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        return subjectRepository.findById(subjectId).orElseThrow(() -> new SubjectNotFoundException(subjectId));
    }

    private ExamBoardRegistration resolveExamBoardRegistration(Long examBoardRegistrationId) {
        if (examBoardRegistrationId == null) {
            return null;
        }
        return examBoardRegistrationRepository.findById(examBoardRegistrationId)
                .orElseThrow(() -> new ExamBoardRegistrationNotFoundException(examBoardRegistrationId));
    }
}
