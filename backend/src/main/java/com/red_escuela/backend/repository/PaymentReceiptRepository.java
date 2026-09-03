package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    List<PaymentReceipt> findByStatusOrderByUploadedAtAsc(PaymentReceiptStatus status);

    Optional<PaymentReceipt> findByInvoiceIdAndStatus(Long invoiceId, PaymentReceiptStatus status);

    List<PaymentReceipt> findByInvoiceIdOrderByUploadedAtDesc(Long invoiceId);
}
