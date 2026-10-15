package com.red_escuela.backend.billing.invoice.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public record ChargeKey(String value) {

    private static final int MAX_LENGTH = 120;
    private static final String PROGRAM_ENROLLMENT_PREFIX = "PE:";
    private static final String COURSE_ENROLLMENT_PREFIX = "CE:";
    private static final String PROGRAM_ADMISSION_PREFIX = "PA:";
    private static final String COURSE_ADMISSION_PREFIX = "CA:";
    private static final String AD_HOC_PREFIX = "AH:";
    private static final String LATE_FEE_PREFIX = "LF:";

    public ChargeKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("A charge key must have between 1 and " + MAX_LENGTH + " characters");
        }
    }

    public static ChargeKey forProgramEnrollmentMonthlyInstallment(Long programEnrollmentId, YearMonth period) {
        return new ChargeKey(PROGRAM_ENROLLMENT_PREFIX + required(programEnrollmentId, "programEnrollmentId") + ":MONTHLY_INSTALLMENT:" + required(period, "period"));
    }

    public static ChargeKey forProgramEnrollmentEnrollmentFee(Long programEnrollmentId, Long templateId, Integer enrollmentFeeSlot) {
        return new ChargeKey(PROGRAM_ENROLLMENT_PREFIX + required(programEnrollmentId, "programEnrollmentId") + ":ENROLLMENT_FEE:T"
                + required(templateId, "templateId") + ":S" + required(enrollmentFeeSlot, "enrollmentFeeSlot"));
    }

    public static ChargeKey forCourseEnrollmentMonthlyInstallment(Long courseEnrollmentId, YearMonth period) {
        return new ChargeKey(COURSE_ENROLLMENT_PREFIX + required(courseEnrollmentId, "courseEnrollmentId") + ":MONTHLY_INSTALLMENT:" + required(period, "period"));
    }

    public static ChargeKey forProgramAdmissionEnrollmentFee(Long studentId, Long templateId, Integer enrollmentFeeSlot) {
        return new ChargeKey(PROGRAM_ADMISSION_PREFIX + required(studentId, "studentId") + ":ENROLLMENT_FEE:T"
                + required(templateId, "templateId") + ":S" + required(enrollmentFeeSlot, "enrollmentFeeSlot"));
    }

    public static ChargeKey forCourseAdmissionMonthlyInstallment(Long studentId, Long courseId, YearMonth period) {
        return new ChargeKey(COURSE_ADMISSION_PREFIX + required(studentId, "studentId") + ":" + required(courseId, "courseId")
                + ":MONTHLY_INSTALLMENT:" + required(period, "period"));
    }

    public static ChargeKey forAdHocCharge(Long studentId, FeeType feeType, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("An ad hoc charge needs an idempotency key");
        }
        return new ChargeKey(AD_HOC_PREFIX + required(studentId, "studentId") + ":" + required(feeType, "feeType") + ":" + idempotencyKey.strip());
    }

    public static ChargeKey forLateFee(Long originInvoiceId, LocalDate periodStart) {
        return new ChargeKey(LATE_FEE_PREFIX + required(originInvoiceId, "originInvoiceId") + ":D" + required(periodStart, "periodStart"));
    }

    private static <T> T required(T value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
