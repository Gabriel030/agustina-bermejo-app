package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentFeeDiscountFacts;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.billing.discount.repository.LiveEnrollmentFeeRepository;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscount;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-416")
class EnrollmentFeeDiscountReaderTest {

    private static final long ENROLLMENT_ID = 7L;
    private static final long INVOICE_ID = 100L;

    private final LiveEnrollmentFeeRepository repository = mock(LiveEnrollmentFeeRepository.class);
    private final ConfiguredLectiveYearReader lectiveYearReader = mock(ConfiguredLectiveYearReader.class);
    private final BillingEnrollmentLookup lookup = mock(BillingEnrollmentLookup.class);
    private final EnrollmentFeeDiscountReader reader = new EnrollmentFeeDiscountReader(repository, lectiveYearReader, lookup);

    private Invoice augustFeeWithDiscount() {
        InvoiceLine line = mock(InvoiceLine.class);
        when(line.getFeeType()).thenReturn(FeeType.ENROLLMENT_FEE);
        when(line.getProgramEnrollmentId()).thenReturn(ENROLLMENT_ID);
        when(line.getCourseEnrollmentId()).thenReturn(null);
        when(line.getPeriod()).thenReturn(YearMonth.of(2028, 8));
        when(line.getDiscount()).thenReturn(mock(InvoiceDiscount.class));
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(INVOICE_ID);
        when(invoice.getLines()).thenReturn(List.of(line));
        when(lookup.findProgramEnrollmentFacts(ENROLLMENT_ID)).thenReturn(Optional.of(mock(BillingEnrollmentFacts.class)));
        when(lectiveYearReader.findLectiveYearWhenConfigured(any(), any(), any())).thenReturn(Optional.of(new LectiveYearSpan(YearMonth.of(2028, 8), YearMonth.of(2029, 7))));
        return invoice;
    }

    @Test
    void anotherLiveFeeInJanuaryOfTheNextCalendarYearIsInTheSameAugustToJulyLectiveYear() {
        Invoice invoice = augustFeeWithDiscount();
        when(repository.findLivePeriodsOfProgramEnrollmentExcludingInvoice(ENROLLMENT_ID, INVOICE_ID)).thenReturn(List.of(YearMonth.of(2029, 1)));

        EnrollmentFeeDiscountFacts facts = reader.describeEnrollmentFeeOf(invoice).orElseThrow();

        assertThat(facts.otherLiveEnrollmentFeeInSameLectiveYear()).isTrue();
        assertThat(facts.reissueWouldLoseDiscount()).isTrue();
    }

    @Test
    void anotherLiveFeeInFebruaryBeforeTheCycleStartsIsInTheSameCalendarYearButNotInTheSameLectiveYear() {
        Invoice invoice = augustFeeWithDiscount();
        when(repository.findLivePeriodsOfProgramEnrollmentExcludingInvoice(ENROLLMENT_ID, INVOICE_ID)).thenReturn(List.of(YearMonth.of(2028, 2)));

        EnrollmentFeeDiscountFacts facts = reader.describeEnrollmentFeeOf(invoice).orElseThrow();

        assertThat(facts.otherLiveEnrollmentFeeInSameLectiveYear()).isFalse();
        assertThat(facts.reissueWouldLoseDiscount()).isFalse();
    }
}
