package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPendingPaymentReceiptsService implements ListPendingPaymentReceiptsUseCase {

    private final BankTransferReceiptRepository bankTransferReceiptRepository;

    public ListPendingPaymentReceiptsService(BankTransferReceiptRepository bankTransferReceiptRepository) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
    }

    @Override
    public List<BankTransferReceipt> list() {
        return bankTransferReceiptRepository.findByStatusWithStudentDetails(BankTransferReceiptStatus.PENDING_REVIEW);
    }
}
