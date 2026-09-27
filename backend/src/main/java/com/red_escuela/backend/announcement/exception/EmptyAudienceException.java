package com.red_escuela.backend.announcement.exception;

public class EmptyAudienceException extends RuntimeException {

    public EmptyAudienceException() {
        super("The selected audience resolves to zero students");
    }
}
