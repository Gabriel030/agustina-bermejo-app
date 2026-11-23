package com.red_escuela.backend.shared.errortracking;

public interface ServerErrorReporter {

    void reportServerError(Throwable throwable);
}
