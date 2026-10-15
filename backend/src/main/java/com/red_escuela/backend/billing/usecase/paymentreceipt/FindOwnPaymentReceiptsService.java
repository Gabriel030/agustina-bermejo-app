package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class FindOwnPaymentReceiptsService implements FindOwnPaymentReceiptsUseCase {

    private final BankTransferReceiptRepository bankTransferReceiptRepository;

    public FindOwnPaymentReceiptsService(BankTransferReceiptRepository bankTransferReceiptRepository) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
    }

    @Override
    public List<BankTransferReceipt> findOwn(Long studentId) {
        return bankTransferReceiptRepository.findByStudentId(studentId);
    }
}
