package com.red_escuela.backend.billing.collections.domain;

import java.util.Objects;

public record ListedOverdueContact(OverdueContact contact, boolean newlyListed, OverdueContactReason entryReason) {

    public ListedOverdueContact {
        Objects.requireNonNull(contact, "contact");
        Objects.requireNonNull(entryReason, "entryReason");
    }
}
