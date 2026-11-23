package com.red_escuela.backend.dataimport.api;

public interface ImportedAccountsQuery {

    ImportedAccountsPage findPendingActivation(Long importBatchId, Long afterPersonId, int limit);
}
