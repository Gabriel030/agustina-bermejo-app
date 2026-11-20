package com.red_escuela.backend.platform.infrastructure;

final class InvalidBackupStatusFileException extends RuntimeException {

    InvalidBackupStatusFileException(String field) {
        super("Archivo de estado de backups inválido en el campo " + field);
    }
}
