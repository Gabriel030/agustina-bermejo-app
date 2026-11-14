package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.exception.ImportConfirmationInvalidException;

public final class ImportConfirmation {
    public static final String SEAL_WORD = "CERRAR";
    public static final String ROLLBACK_WORD = "DESHACER";

    private ImportConfirmation() {
    }

    public static void require(String typed, String expectedWord) {
        if (typed == null || !typed.trim().equalsIgnoreCase(expectedWord)) {
            throw new ImportConfirmationInvalidException(expectedWord);
        }
    }
}
