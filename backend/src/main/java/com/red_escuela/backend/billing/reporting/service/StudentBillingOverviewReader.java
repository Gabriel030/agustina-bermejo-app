package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.dto.AdminEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.service.InvoiceDetailLoader;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Component
public class StudentBillingOverviewReader {
    private final StudentRepository studentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final InvoiceDetailLoader invoiceDetailLoader;

    public StudentBillingOverviewReader(StudentRepository studentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                            ProgramEnrollmentRepository programEnrollmentRepository, InvoiceDetailLoader invoiceDetailLoader) {
        this.studentRepository = studentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.invoiceDetailLoader = invoiceDetailLoader;
    }

    @Transactional(readOnly = true)
    public List<AdminEnrollmentBillingStatusResponse> readStudentBillingOverview(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException(studentId);
        }
        return Stream.concat(
                courseEnrollmentRepository.findByStudentId(studentId).stream()
                        .map(enrollment -> AdminEnrollmentBillingStatusResponse.of(enrollment, invoiceDetailLoader.findStandingOfCourseEnrollmentOrNull(enrollment.getId()), invoiceDetailLoader.byCourseEnrollment(enrollment.getId()))),
                programEnrollmentRepository.findByStudentId(studentId).stream()
                        .map(enrollment -> AdminEnrollmentBillingStatusResponse.of(enrollment, invoiceDetailLoader.findStandingOfProgramEnrollmentOrNull(enrollment.getId()), invoiceDetailLoader.byProgramEnrollment(enrollment.getId()))))
                .toList();
    }
}
