package com.red_escuela.backend.platform.infrastructure;

final class InvalidBackupStatusFileException extends RuntimeException {

    private final String field;

    InvalidBackupStatusFileException(String field) {
        super("Archivo de estado de backups inválido en el campo " + field);
        this.field = field;
    }

    String field() {
        return field;
    }
}
