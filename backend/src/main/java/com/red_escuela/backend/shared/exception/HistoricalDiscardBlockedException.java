package com.red_escuela.backend.shared.exception;

public class HistoricalDiscardBlockedException extends ConflictException {
    public HistoricalDiscardBlockedException(String reason) {
        super(reason);
    }
}
