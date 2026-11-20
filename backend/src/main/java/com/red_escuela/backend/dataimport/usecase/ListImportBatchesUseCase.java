package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import java.util.List;

public interface ListImportBatchesUseCase {
    List<ImportBatchResponse> list();
}
