package com.red_escuela.backend.dataimport.dto;

public record CommitImportBatchRequest(Boolean onlyValidRows) {

    public boolean onlyValidRowsRequested() {
        return Boolean.TRUE.equals(onlyValidRows);
    }
}
