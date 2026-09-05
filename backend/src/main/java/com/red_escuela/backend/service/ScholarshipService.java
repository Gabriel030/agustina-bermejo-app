package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateScholarshipRequest;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Scholarship;
import com.red_escuela.backend.entity.ScholarshipStatus;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ScholarshipExceedsCohortSpanException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ScholarshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScholarshipService {

    private final ScholarshipRepository scholarshipRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final RecurringFeeWindowService recurringFeeWindowService;
    private final CurrentPersonService currentPersonService;

    public ScholarshipService(ScholarshipRepository scholarshipRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                               ProgramEnrollmentRepository programEnrollmentRepository, RecurringFeeWindowService recurringFeeWindowService,
                               CurrentPersonService currentPersonService) {
        this.scholarshipRepository = scholarshipRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.recurringFeeWindowService = recurringFeeWindowService;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Scholarship grantForCourse(Long courseEnrollmentId, CreateScholarshipRequest request) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        validateInstallmentsWithinCohortSpan(request.installments(), recurringFeeWindowService.remainingInvoices(enrollment));
        Scholarship scholarship = build(request, currentPersonService.resolveCurrentPerson());
        scholarship.setCourseEnrollment(enrollment);
        return scholarshipRepository.save(scholarship);
    }

    @Transactional
    public Scholarship grantForProgram(Long programEnrollmentId, CreateScholarshipRequest request) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        validateInstallmentsWithinCohortSpan(request.installments(), recurringFeeWindowService.remainingInvoices(enrollment));
        Scholarship scholarship = build(request, currentPersonService.resolveCurrentPerson());
        scholarship.setProgramEnrollment(enrollment);
        return scholarshipRepository.save(scholarship);
    }

    @Transactional
    public void cancelActiveForCourse(Long courseEnrollmentId, String reason) {
        scholarshipRepository.findByCourseEnrollmentIdAndStatus(courseEnrollmentId, ScholarshipStatus.ACTIVE)
                .ifPresent(scholarship -> {
                    scholarship.cancelRemaining(reason);
                    scholarshipRepository.save(scholarship);
                });
    }

    @Transactional
    public void cancelActiveForProgram(Long programEnrollmentId, String reason) {
        scholarshipRepository.findByProgramEnrollmentIdAndStatus(programEnrollmentId, ScholarshipStatus.ACTIVE)
                .ifPresent(scholarship -> {
                    scholarship.cancelRemaining(reason);
                    scholarshipRepository.save(scholarship);
                });
    }

    private Scholarship build(CreateScholarshipRequest request, Person actor) {
        Scholarship scholarship = new Scholarship();
        scholarship.setValue(request.value().toValue());
        scholarship.setReason(request.reason());
        scholarship.setGrantedBy(actor);
        scholarship.addInstallments(request.installments());
        return scholarship;
    }

    private void validateInstallmentsWithinCohortSpan(int installments, Integer maxInstallments) {
        if (maxInstallments != null && installments > maxInstallments) {
            throw new ScholarshipExceedsCohortSpanException(installments, maxInstallments);
        }
    }
}
