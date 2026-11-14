package com.red_escuela.backend.identity.dto;

import java.time.LocalDate;
import java.util.List;

public record PendingLegalAcceptancesResponse(List<LegalDocumentResponse> documents, LocalDate suggestedDeadline,
                                              boolean gracePeriodExpired, boolean guardianConfirmationRequired) {
}
