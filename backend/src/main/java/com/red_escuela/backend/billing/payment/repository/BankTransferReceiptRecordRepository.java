package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptRecord;
import org.springframework.data.repository.Repository;

public interface BankTransferReceiptRecordRepository extends Repository<BankTransferReceiptRecord, Long> {

    BankTransferReceiptRecord save(BankTransferReceiptRecord receipt);
}
