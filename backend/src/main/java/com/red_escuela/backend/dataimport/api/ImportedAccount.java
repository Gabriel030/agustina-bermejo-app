package com.red_escuela.backend.dataimport.api;

public record ImportedAccount(Long personId, String email, String displayName, Long importBatchId) {

    public boolean withoutEmail() {
        return email == null || email.isBlank();
    }
}
