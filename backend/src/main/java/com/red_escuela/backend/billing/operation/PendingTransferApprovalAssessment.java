package com.red_escuela.backend.billing.operation;

import java.time.LocalDate;

public record PendingTransferApprovalAssessment(LocalDate uploadDate, LocalDate verifiedTransferDate) {

    public boolean transferDateMatchesUploadDate() {
        return uploadDate.equals(verifiedTransferDate);
    }
}
