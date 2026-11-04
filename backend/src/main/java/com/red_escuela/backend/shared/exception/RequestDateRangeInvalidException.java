package com.red_escuela.backend.shared.exception;

public class RequestDateRangeInvalidException extends BadRequestException {
    public RequestDateRangeInvalidException(String message) {
        super(message);
    }
}
