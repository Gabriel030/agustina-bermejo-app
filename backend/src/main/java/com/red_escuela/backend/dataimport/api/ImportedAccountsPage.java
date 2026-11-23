package com.red_escuela.backend.dataimport.api;

import java.util.List;

public record ImportedAccountsPage(List<ImportedAccount> accounts, Long nextAfterPersonId) {

    public boolean hasMore() {
        return nextAfterPersonId != null;
    }
}
