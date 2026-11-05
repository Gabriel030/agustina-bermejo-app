package com.red_escuela.backend.enrollment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AdmissionApplicationExpiryPort {

    List<Long> findApprovedApplicationIdsWithAdmissionClosedBefore(LocalDate today);

    Optional<Long> findAdmissionInvoiceIdOfApprovedApplicationWithAdmissionClosedBefore(Long applicationId, LocalDate today);

    boolean expireIfApproved(Long applicationId);
}
