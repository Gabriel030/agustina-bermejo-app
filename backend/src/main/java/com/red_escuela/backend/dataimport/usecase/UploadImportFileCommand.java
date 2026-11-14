package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;

public record UploadImportFileCommand(Long batchId, ImportEntityType entityType, String originalName, byte[] content) {
}
