package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.IssuingTestData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-009")
@Tag("D-021")
class ChargeKeyTest {

    @Test
    void factoriesBuildTheDocumentedKeys() {
        assertThat(ChargeKey.forProgramEnrollmentMonthlyInstallment(12L, YearMonth.of(2027, 3)).value()).isEqualTo("PE:12:MONTHLY_INSTALLMENT:2027-03");
        assertThat(ChargeKey.forProgramEnrollmentEnrollmentFee(12L, 45L, 1).value()).isEqualTo("PE:12:ENROLLMENT_FEE:T45:S1");
        assertThat(ChargeKey.forCourseEnrollmentMonthlyInstallment(7L, YearMonth.of(2027, 3)).value()).isEqualTo("CE:7:MONTHLY_INSTALLMENT:2027-03");
        assertThat(ChargeKey.forProgramAdmissionEnrollmentFee(3L, 45L, 2).value()).isEqualTo("PA:3:ENROLLMENT_FEE:T45:S2");
        assertThat(ChargeKey.forCourseAdmissionMonthlyInstallment(3L, 8L, YearMonth.of(2027, 3)).value()).isEqualTo("CA:3:8:MONTHLY_INSTALLMENT:2027-03");
        assertThat(ChargeKey.forAdHocCharge(4L, FeeType.EXAM_FEE, " abc ").value()).isEqualTo("AH:4:EXAM_FEE:abc");
        assertThat(ChargeKey.forAdHocCharge(5L, FeeType.EXAM_FEE, "abc")).isNotEqualTo(ChargeKey.forAdHocCharge(4L, FeeType.EXAM_FEE, "abc"));
    }

    @Test
    void invalidKeysAreRejected() {
        assertThatThrownBy(() -> new ChargeKey(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChargeKey("x".repeat(121))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ChargeKey("x".repeat(120)).value()).hasSize(120);
        assertThatThrownBy(() -> ChargeKey.forAdHocCharge(1L, FeeType.EXAM_FEE, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeKey.forAdHocCharge(1L, FeeType.EXAM_FEE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeKey.forProgramEnrollmentMonthlyInstallment(null, YearMonth.of(2027, 3))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theRequestPicksTheKeyOfItsKind() {
        InvoiceIssuingRequest installment = IssuingTestData.monthly();
        InvoiceIssuingRequest fee = IssuingTestData.request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, IssuingTestData.TODAY, 4L, 2);
        InvoiceIssuingRequest adHoc = IssuingTestData.request(FeeType.EXAM_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, IssuingTestData.TODAY, null, null);

        assertThat(installment.chargeKey().value()).isEqualTo("PE:10:MONTHLY_INSTALLMENT:2027-03");
        assertThat(fee.chargeKey().value()).isEqualTo("PE:10:ENROLLMENT_FEE:T4:S2");
        assertThat(adHoc.chargeKey().value()).isEqualTo("AH:1:EXAM_FEE:key-1");
    }

    @Test
    void applicantRequestsUseTheAdmissionKeys() {
        var program = new InvoiceIssuingRequest(3L, null, null, 5L, null, 2L, FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.PROGRAM_ADMISSION, null, 4L, 1, true, null, null, null, null, null, IssuingTestData.ACTOR);
        var course = new InvoiceIssuingRequest(3L, null, null, null, 8L, null, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.COURSE_ADMISSION, null, null, null, false, null, null, null, null, null, IssuingTestData.ACTOR);
        var courseEnrollment = new InvoiceIssuingRequest(3L, null, 9L, null, 8L, null, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.COURSE_MONTHLY_RUN, null, null, null, false, null, null, null, null, null, IssuingTestData.ACTOR);

        assertThat(program.chargeKey().value()).isEqualTo("PA:3:ENROLLMENT_FEE:T4:S1");
        assertThat(course.chargeKey().value()).isEqualTo("CA:3:8:MONTHLY_INSTALLMENT:2027-03");
        assertThat(courseEnrollment.chargeKey().value()).isEqualTo("CE:9:MONTHLY_INSTALLMENT:2027-03");
    }

    @Test
    void anEnrollmentFeeWithoutTemplateOrSlotHasNoKeyYetAndACourseNeverHasOne() {
        assertThat(IssuingTestData.request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, IssuingTestData.TODAY, null, 1).findChargeKey()).isEmpty();
        assertThat(IssuingTestData.request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, IssuingTestData.TODAY, 2L, null).findChargeKey()).isEmpty();
        var courseFee = new InvoiceIssuingRequest(3L, null, null, null, 8L, null, FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.COURSE_ADMISSION, null, 4L, 1, true, null, null, null, null, null, IssuingTestData.ACTOR);
        assertThat(courseFee.findChargeKey()).isEmpty();
        var programMonthlyWithoutEnrollment = new InvoiceIssuingRequest(3L, null, null, 5L, null, null, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3),
                IssuingTestData.TODAY, IssuingTestData.TODAY, InvoiceIssuingReason.MONTHLY_RUN, null, null, null, false, null, null, null, null, null, IssuingTestData.ACTOR);
        assertThat(programMonthlyWithoutEnrollment.findChargeKey()).isEmpty();
        assertThatThrownBy(programMonthlyWithoutEnrollment::chargeKey).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anEnrollmentRequestListsTheAdmissionKeyItMustReconcileWith() {
        InvoiceIssuingRequest fee = IssuingTestData.request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, IssuingTestData.TODAY, 4L, 2);
        var courseEnrollment = new InvoiceIssuingRequest(3L, null, 9L, null, 8L, null, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.COURSE_MONTHLY_RUN, null, null, null, false, null, null, null, null, null, IssuingTestData.ACTOR);

        assertThat(fee.findEquivalentAdmissionChargeKeys()).extracting(ChargeKey::value).containsExactly("PA:1:ENROLLMENT_FEE:T4:S2");
        assertThat(courseEnrollment.findEquivalentAdmissionChargeKeys()).extracting(ChargeKey::value).containsExactly("CA:3:8:MONTHLY_INSTALLMENT:2027-03");
        assertThat(IssuingTestData.monthly().findEquivalentAdmissionChargeKeys()).isEmpty();
        assertThat(IssuingTestData.request(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, IssuingTestData.TODAY, null, null).findEquivalentAdmissionChargeKeys()).isEmpty();
        assertThat(IssuingTestData.request(FeeType.EXAM_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, IssuingTestData.TODAY, null, null).findEquivalentAdmissionChargeKeys()).isEmpty();
    }

    @Test
    void requestShapeIsValidated() {
        assertThatThrownBy(() -> new InvoiceIssuingRequest(1L, 1L, 2L, 5L, null, null, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.MONTHLY_RUN, null, null, null, false, null, null, null, null, null, IssuingTestData.ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceIssuingRequest(1L, null, null, 5L, 6L, null, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.MONTHLY_RUN, null, null, null, false, null, null, null, null, null, IssuingTestData.ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceIssuingRequest(1L, null, 3L, 5L, null, null, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.MONTHLY_RUN, null, null, null, false, null, null, null, null, null, IssuingTestData.ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceIssuingRequest(1L, null, null, 5L, null, null, FeeType.EXAM_FEE, YearMonth.of(2027, 3), IssuingTestData.TODAY,
                IssuingTestData.TODAY, InvoiceIssuingReason.AD_HOC_CHARGE, null, null, null, false, null, null, null, null, null, IssuingTestData.ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyIssuableFeeTypeMapsToItsInvoiceType() {
        for (FeeType feeType : FeeType.values()) {
            InvoiceIssuingRequest request = IssuingTestData.request(feeType, InvoiceIssuingReason.MONTHLY_RUN, IssuingTestData.TODAY, 1L, 1);
            assertThat(request.invoiceType().name()).isEqualTo(feeType.name());
        }
    }
}
