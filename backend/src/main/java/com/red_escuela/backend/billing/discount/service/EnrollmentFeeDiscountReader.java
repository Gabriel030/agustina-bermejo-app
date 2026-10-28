package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentFeeDiscountFacts;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.billing.discount.repository.LiveEnrollmentFeeRepository;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class EnrollmentFeeDiscountReader {
    private final LiveEnrollmentFeeRepository repository;
    private final ConfiguredLectiveYearReader lectiveYearReader;
    private final BillingEnrollmentLookup enrollmentLookup;

    public EnrollmentFeeDiscountReader(LiveEnrollmentFeeRepository repository, ConfiguredLectiveYearReader lectiveYearReader, BillingEnrollmentLookup enrollmentLookup) {
        this.repository = repository;
        this.lectiveYearReader = lectiveYearReader;
        this.enrollmentLookup = enrollmentLookup;
    }

    @Transactional(readOnly = true)
    public Optional<EnrollmentFeeDiscountFacts> describeEnrollmentFeeOf(Invoice invoice) {
        return invoice.getLines().stream().filter(line -> line.getFeeType() == FeeType.ENROLLMENT_FEE).findFirst()
                .map(line -> new EnrollmentFeeDiscountFacts(line.getDiscount() != null, otherLiveEnrollmentFeeInSameLectiveYear(invoice.getId(), line)));
    }

    private boolean otherLiveEnrollmentFeeInSameLectiveYear(Long invoiceId, InvoiceLine line) {
        if (line.getCourseEnrollmentId() != null) {
            return repository.countLiveOfCourseEnrollmentExcludingInvoice(line.getCourseEnrollmentId(), invoiceId) > 0;
        }
        if (line.getProgramEnrollmentId() == null || line.getPeriod() == null) {
            return false;
        }
        EnrollmentReference enrollment = EnrollmentReference.ofProgram(line.getProgramEnrollmentId());
        Optional<BillingEnrollmentFacts> facts = enrollmentLookup.findProgramEnrollmentFacts(line.getProgramEnrollmentId());
        Optional<LectiveYearSpan> lectiveYear = facts.flatMap(found -> lectiveYearReader.findLectiveYearWhenConfigured(enrollment, found, line.getPeriod()));
        return lectiveYear.isPresent() && repository.findLivePeriodsOfProgramEnrollmentExcludingInvoice(line.getProgramEnrollmentId(), invoiceId).stream()
                .anyMatch(lectiveYear.get()::contains);
    }
}
