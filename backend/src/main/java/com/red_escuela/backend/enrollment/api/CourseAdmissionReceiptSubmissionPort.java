package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Money;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface CourseAdmissionReceiptSubmissionPort {

    Long submitReceiptCoveringCourseAdmission(Long ownStudentId, Long admissionInvoiceId, MultipartFile file, List<Long> olderInvoiceIds, Money declaredAmount,
                                              LocalDate transferDate, String operationNumber, String originAccount, String holderName,
                                              String bankName, String idempotencyKey);
}
