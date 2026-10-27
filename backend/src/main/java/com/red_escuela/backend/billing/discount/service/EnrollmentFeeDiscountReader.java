package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentFeeDiscountFacts;
import com.red_escuela.backend.billing.discount.repository.LiveEnrollmentFeeRepository;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Optional;

@Component
public class EnrollmentFeeDiscountReader {
    private final LiveEnrollmentFeeRepository repository;

    public EnrollmentFeeDiscountReader(LiveEnrollmentFeeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<EnrollmentFeeDiscountFacts> describeEnrollmentFeeOf(Invoice invoice) {
        return invoice.getLines().stream().filter(line -> line.getFeeType() == FeeType.ENROLLMENT_FEE).findFirst()
                .map(line -> new EnrollmentFeeDiscountFacts(line.getDiscount() != null, otherLiveEnrollmentFeeInSameCalendarYear(invoice.getId(), line)));
    }

    private boolean otherLiveEnrollmentFeeInSameCalendarYear(Long invoiceId, InvoiceLine line) {
        if (line.getCourseEnrollmentId() != null) {
            return repository.countLiveOfCourseEnrollmentExcludingInvoice(line.getCourseEnrollmentId(), invoiceId) > 0;
        }
        if (line.getProgramEnrollmentId() == null || line.getPeriod() == null) {
            return false;
        }
        return repository.findLivePeriodsOfProgramEnrollmentExcludingInvoice(line.getProgramEnrollmentId(), invoiceId).stream()
                .map(YearMonth::getYear).anyMatch(year -> year == line.getPeriod().getYear());
    }
}
