package com.red_escuela.backend.graduation.debtexception.port;

public record DebtExceptionDocument(String code, byte[] content, String auditDetail) {
}
