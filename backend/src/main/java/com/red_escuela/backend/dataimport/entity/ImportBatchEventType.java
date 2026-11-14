package com.red_escuela.backend.dataimport.entity;

public enum ImportBatchEventType {
    CREATED, FILE_UPLOADED, VALIDATED, COMMITTED, RETRIED, ROLLED_BACK, SEALED
}
