package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.payment.dto.BankTransferReceiptSubmissionRequest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class TransferReceiptFixtures {
    private static final AtomicLong OPERATION_SEQUENCE = new AtomicLong(1);

    private TransferReceiptFixtures() {
    }

    public static BankTransferReceiptSubmissionRequest transferReceiptOf(long amount) {
        return transferReceiptOf(amount, List.of());
    }

    public static BankTransferReceiptSubmissionRequest transferReceiptOf(long amount, List<Long> additionalInvoiceIds) {
        return BankTransferReceiptSubmissionRequest.fromForm(pdfFile(), additionalInvoiceIds, BigDecimal.valueOf(amount), Currency.ARS, ApplicationTime.today(),
                "OP-" + OPERATION_SEQUENCE.getAndIncrement(), "0170099220000067797370", "Ana Pérez", "Banco de prueba");
    }

    public static Long signedInAs(Student student) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(student.getPerson().getId().toString(), null, "ROLE_PROSPECT"));
        return student.getId();
    }

    public static MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "comprobante.pdf", "application/pdf", "%PDF-1.4 contenido de prueba".getBytes());
    }

    public static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }
}
