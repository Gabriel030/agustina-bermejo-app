package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.Money;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public record CourseTransferReceiptForm(MultipartFile file, List<Long> olderInvoiceIds, Money declaredAmount, LocalDate transferDate,
                                        String operationNumber, String originAccount, String holderName, String bankName) {
}
