package com.red_escuela.backend.dataimport.controller;

import com.red_escuela.backend.dataimport.dto.CommitImportBatchRequest;
import com.red_escuela.backend.dataimport.dto.CreateImportBatchRequest;
import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.dto.ImportRowErrorResponse;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.exception.ImportFileUnreadableException;
import com.red_escuela.backend.dataimport.usecase.CommitImportBatchUseCase;
import com.red_escuela.backend.dataimport.usecase.CreateImportBatchUseCase;
import com.red_escuela.backend.dataimport.usecase.GetImportBatchUseCase;
import com.red_escuela.backend.dataimport.usecase.ListImportBatchesUseCase;
import com.red_escuela.backend.dataimport.usecase.ListImportRowErrorsUseCase;
import com.red_escuela.backend.dataimport.usecase.UploadImportFileCommand;
import com.red_escuela.backend.dataimport.usecase.UploadImportFileUseCase;
import com.red_escuela.backend.dataimport.usecase.ValidateImportBatchUseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/data-imports")
public class DataImportController {
    private final CreateImportBatchUseCase createImportBatchUseCase;
    private final UploadImportFileUseCase uploadImportFileUseCase;
    private final ValidateImportBatchUseCase validateImportBatchUseCase;
    private final CommitImportBatchUseCase commitImportBatchUseCase;
    private final GetImportBatchUseCase getImportBatchUseCase;
    private final ListImportBatchesUseCase listImportBatchesUseCase;
    private final ListImportRowErrorsUseCase listImportRowErrorsUseCase;

    public DataImportController(CreateImportBatchUseCase createImportBatchUseCase,
            UploadImportFileUseCase uploadImportFileUseCase, ValidateImportBatchUseCase validateImportBatchUseCase,
            CommitImportBatchUseCase commitImportBatchUseCase, GetImportBatchUseCase getImportBatchUseCase,
            ListImportBatchesUseCase listImportBatchesUseCase, ListImportRowErrorsUseCase listImportRowErrorsUseCase) {
        this.createImportBatchUseCase = createImportBatchUseCase;
        this.uploadImportFileUseCase = uploadImportFileUseCase;
        this.validateImportBatchUseCase = validateImportBatchUseCase;
        this.commitImportBatchUseCase = commitImportBatchUseCase;
        this.getImportBatchUseCase = getImportBatchUseCase;
        this.listImportBatchesUseCase = listImportBatchesUseCase;
        this.listImportRowErrorsUseCase = listImportRowErrorsUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ImportBatchResponse createBatch(@Valid @RequestBody CreateImportBatchRequest request) {
        return createImportBatchUseCase.create(request.name());
    }

    @GetMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<ImportBatchResponse> listBatches() {
        return listImportBatchesUseCase.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ImportBatchResponse getBatch(@PathVariable Long id) {
        return getImportBatchUseCase.get(id);
    }

    @PostMapping(value = "/{id}/files/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ImportBatchResponse uploadFile(@PathVariable Long id, @PathVariable ImportEntityType entityType,
            @RequestParam("file") MultipartFile file) {
        try {
            String name = file.getOriginalFilename() == null ? "archivo.csv" : file.getOriginalFilename();
            return uploadImportFileUseCase.upload(new UploadImportFileCommand(id, entityType, name, file.getBytes()));
        } catch (IOException e) {
            throw new ImportFileUnreadableException();
        }
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ImportBatchResponse validateBatch(@PathVariable Long id) {
        return validateImportBatchUseCase.validate(id);
    }

    @PostMapping("/{id}/commit")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ImportBatchResponse commitBatch(@PathVariable Long id,
            @Valid @RequestBody(required = false) CommitImportBatchRequest request) {
        return commitImportBatchUseCase.commit(id, request != null && request.onlyValidRowsRequested());
    }

    @GetMapping("/{id}/errors")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PageResponse<ImportRowErrorResponse> listErrors(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return listImportRowErrorsUseCase.list(id, page, size);
    }
}
