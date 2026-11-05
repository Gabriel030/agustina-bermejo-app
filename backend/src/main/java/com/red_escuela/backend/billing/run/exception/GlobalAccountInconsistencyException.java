package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GlobalAccountInconsistencyException extends ConflictException {

    public GlobalAccountInconsistencyException(int inconsistentAccounts, int totalAccounts) {
        super("Global account inconsistency: " + inconsistentAccounts + " of " + totalAccounts + " accounts do not close");
    }
}
