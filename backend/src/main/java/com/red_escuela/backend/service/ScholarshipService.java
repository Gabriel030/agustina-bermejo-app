package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateScholarshipRequest;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Scholarship;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ScholarshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

@Service
public class ScholarshipService {

    private final ScholarshipRepository scholarshipRepository;
    private final InvoiceRepository invoiceRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CurrentPersonService currentPersonService;

    public ScholarshipService(ScholarshipRepository scholarshipRepository, InvoiceRepository invoiceRepository,
                               CourseEnrollmentRepository courseEnrollmentRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                               CurrentPersonService currentPersonService) {
        this.scholarshipRepository = scholarshipRepository;
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Scholarship grantForCourse(Long courseEnrollmentId, CreateScholarshipRequest request) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        Person actor = currentPersonService.resolveCurrentPerson();
        Scholarship scholarship = build(request, actor);
        scholarship.setCourseEnrollment(enrollment);
        scholarship = scholarshipRepository.save(scholarship);

        applyRetroactively(request,
                invoiceRepository.findByCourseEnrollmentIdAndPeriodBetween(courseEnrollmentId, YearMonth.from(request.validFrom()), untilPeriod(request)),
                actor);
        return scholarship;
    }

    @Transactional
    public Scholarship grantForProgram(Long programEnrollmentId, CreateScholarshipRequest request) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        Person actor = currentPersonService.resolveCurrentPerson();
        Scholarship scholarship = build(request, actor);
        scholarship.setProgramEnrollment(enrollment);
        scholarship = scholarshipRepository.save(scholarship);

        applyRetroactively(request,
                invoiceRepository.findByProgramEnrollmentIdAndPeriodBetween(programEnrollmentId, YearMonth.from(request.validFrom()), untilPeriod(request)),
                actor);
        return scholarship;
    }

    private Scholarship build(CreateScholarshipRequest request, Person actor) {
        Scholarship scholarship = new Scholarship();
        scholarship.setValue(request.value().toValue());
        scholarship.setReason(request.reason());
        scholarship.setValidFrom(request.validFrom());
        scholarship.setValidUntil(request.validUntil());
        scholarship.setGrantedBy(actor);
        return scholarship;
    }

    private YearMonth untilPeriod(CreateScholarshipRequest request) {
        return request.validUntil() != null ? YearMonth.from(request.validUntil()) : YearMonth.now();
    }

    private void applyRetroactively(CreateScholarshipRequest request, List<Invoice> invoicesInRange, Person actor) {
        MonetaryAdjustmentValue value = request.value().toValue();
        for (Invoice invoice : invoicesInRange) {
            if (invoice.getStatus() == InvoiceStatus.PENDING || invoice.getStatus() == InvoiceStatus.OVERDUE) {
                invoice.applyAdjustment(InvoiceAdjustmentType.SCHOLARSHIP, value, request.reason(), actor);
                invoiceRepository.save(invoice);
            }
        }
    }
}
