package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.domain.EmailAddress;

public final class MaskedEmailAddress {

    private static final int VISIBLE_LOCAL_CHARACTERS = 2;
    private static final String HIDDEN = "***";

    private MaskedEmailAddress() {
    }

    public static String of(EmailAddress address) {
        String value = address.value();
        int at = value.indexOf('@');
        String local = value.substring(0, at);
        String visible = local.substring(0, Math.min(VISIBLE_LOCAL_CHARACTERS, local.length() - 1));
        return visible + HIDDEN + value.substring(at);
    }
}
