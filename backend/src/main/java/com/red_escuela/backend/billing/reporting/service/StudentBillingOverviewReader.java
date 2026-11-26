package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.dto.AdminEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.service.InvoiceDetailLoader;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Component
public class StudentBillingOverviewReader {
    private final StudentRepository studentRepository;
    private final CourseEnrollmentFinder courseEnrollmentFinder;
    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final InvoiceDetailLoader invoiceDetailLoader;

    public StudentBillingOverviewReader(StudentRepository studentRepository, CourseEnrollmentFinder courseEnrollmentFinder,
                                            ProgramEnrollmentFinder programEnrollmentFinder, InvoiceDetailLoader invoiceDetailLoader) {
        this.studentRepository = studentRepository;
        this.courseEnrollmentFinder = courseEnrollmentFinder;
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.invoiceDetailLoader = invoiceDetailLoader;
    }

    @Transactional(readOnly = true)
    public List<AdminEnrollmentBillingStatusResponse> readStudentBillingOverview(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException(studentId);
        }
        return Stream.concat(
                courseEnrollmentFinder.findByStudent(studentId).stream()
                        .map(enrollment -> AdminEnrollmentBillingStatusResponse.of(enrollment, invoiceDetailLoader.findStandingOfCourseEnrollmentOrNull(enrollment.getId()), invoiceDetailLoader.byCourseEnrollment(enrollment.getId()))),
                programEnrollmentFinder.findByStudent(studentId).stream()
                        .map(enrollment -> AdminEnrollmentBillingStatusResponse.of(enrollment, invoiceDetailLoader.findStandingOfProgramEnrollmentOrNull(enrollment.getId()), invoiceDetailLoader.byProgramEnrollment(enrollment.getId()))))
                .toList();
    }
}
