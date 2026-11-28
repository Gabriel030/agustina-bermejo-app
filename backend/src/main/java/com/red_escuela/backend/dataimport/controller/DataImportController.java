package com.red_escuela.backend.dataimport.controller;

import com.red_escuela.backend.dataimport.dto.CommitImportBatchRequest;
import com.red_escuela.backend.dataimport.dto.ConfirmImportActionRequest;
import com.red_escuela.backend.dataimport.dto.ImportFileDownload;
import com.red_escuela.backend.dataimport.dto.ImportRollbackPreviewResponse;
import com.red_escuela.backend.dataimport.usecase.DownloadImportReportUseCase;
import com.red_escuela.backend.dataimport.usecase.DownloadImportTemplateUseCase;
import com.red_escuela.backend.dataimport.usecase.PreviewImportRollbackUseCase;
import com.red_escuela.backend.dataimport.usecase.RetryImportBatchUseCase;
import com.red_escuela.backend.dataimport.usecase.RollbackImportBatchUseCase;
import com.red_escuela.backend.dataimport.usecase.SealImportBatchUseCase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import com.red_escuela.backend.dataimport.dto.CreateImportBatchRequest;
import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.dto.ImportRowErrorResponse;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.exception.ImportFileTooLargeException;
import com.red_escuela.backend.dataimport.exception.ImportFileUnreadableException;
import com.red_escuela.backend.dataimport.specialist.ImportFileStager;
import com.red_escuela.backend.dataimport.usecase.CommitImportBatchUseCase;
import com.red_escuela.backend.dataimport.usecase.CreateImportBatchUseCase;
import com.red_escuela.backend.dataimport.usecase.GetImportBatchUseCase;
import com.red_escuela.backend.dataimport.usecase.ListImportBatchesUseCase;
import com.red_escuela.backend.dataimport.usecase.ListImportRowErrorsUseCase;
import com.red_escuela.backend.dataimport.usecase.UploadImportFileCommand;
import com.red_escuela.backend.dataimport.usecase.UploadImportFileUseCase;
import com.red_escuela.backend.dataimport.usecase.ValidateImportBatchUseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final RetryImportBatchUseCase retryImportBatchUseCase;
    private final SealImportBatchUseCase sealImportBatchUseCase;
    private final RollbackImportBatchUseCase rollbackImportBatchUseCase;
    private final PreviewImportRollbackUseCase previewImportRollbackUseCase;
    private final DownloadImportReportUseCase downloadImportReportUseCase;
    private final DownloadImportTemplateUseCase downloadImportTemplateUseCase;

    public DataImportController(CreateImportBatchUseCase createImportBatchUseCase,
            UploadImportFileUseCase uploadImportFileUseCase, ValidateImportBatchUseCase validateImportBatchUseCase,
            CommitImportBatchUseCase commitImportBatchUseCase, GetImportBatchUseCase getImportBatchUseCase,
            ListImportBatchesUseCase listImportBatchesUseCase, ListImportRowErrorsUseCase listImportRowErrorsUseCase,
            RetryImportBatchUseCase retryImportBatchUseCase, SealImportBatchUseCase sealImportBatchUseCase,
            RollbackImportBatchUseCase rollbackImportBatchUseCase,
            PreviewImportRollbackUseCase previewImportRollbackUseCase,
            DownloadImportReportUseCase downloadImportReportUseCase,
            DownloadImportTemplateUseCase downloadImportTemplateUseCase) {
        this.createImportBatchUseCase = createImportBatchUseCase;
        this.uploadImportFileUseCase = uploadImportFileUseCase;
        this.validateImportBatchUseCase = validateImportBatchUseCase;
        this.commitImportBatchUseCase = commitImportBatchUseCase;
        this.getImportBatchUseCase = getImportBatchUseCase;
        this.listImportBatchesUseCase = listImportBatchesUseCase;
        this.listImportRowErrorsUseCase = listImportRowErrorsUseCase;
        this.retryImportBatchUseCase = retryImportBatchUseCase;
        this.sealImportBatchUseCase = sealImportBatchUseCase;
        this.rollbackImportBatchUseCase = rollbackImportBatchUseCase;
        this.previewImportRollbackUseCase = previewImportRollbackUseCase;
        this.downloadImportReportUseCase = downloadImportReportUseCase;
        this.downloadImportTemplateUseCase = downloadImportTemplateUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "dataimport.create", legacyRoles = "ROLE_DIRECTOR")
    public ImportBatchResponse createBatch(@Valid @RequestBody CreateImportBatchRequest request) {
        return createImportBatchUseCase.create(request.name());
    }

    @GetMapping
    @RequiresPermission(code = "dataimport.read", legacyRoles = "ROLE_DIRECTOR")
    public List<ImportBatchResponse> listBatches() {
        return listImportBatchesUseCase.list();
    }

    @GetMapping("/{id}")
    @RequiresPermission(code = "dataimport.read", legacyRoles = "ROLE_DIRECTOR")
    public ImportBatchResponse getBatch(@PathVariable Long id) {
        return getImportBatchUseCase.get(id);
    }

    @PostMapping(value = "/{id}/files/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(code = "dataimport.upload", legacyRoles = "ROLE_DIRECTOR")
    public ImportBatchResponse uploadFile(@PathVariable Long id, @PathVariable ImportEntityType entityType,
            @RequestParam("file") MultipartFile file) {
        if (file.getSize() > ImportFileStager.MAX_FILE_BYTES) {
            throw new ImportFileTooLargeException(ImportFileStager.MAX_FILE_BYTES);
        }
        try {
            String name = file.getOriginalFilename() == null ? "archivo.csv" : file.getOriginalFilename();
            return uploadImportFileUseCase.upload(new UploadImportFileCommand(id, entityType, name, file.getBytes()));
        } catch (IOException e) {
            throw new ImportFileUnreadableException();
        }
    }

    @PostMapping("/{id}/validate")
    @RequiresPermission(code = "dataimport.validate", legacyRoles = "ROLE_DIRECTOR")
    public ImportBatchResponse validateBatch(@PathVariable Long id) {
        return validateImportBatchUseCase.validate(id);
    }

    @PostMapping("/{id}/commit")
    @RequiresPermission(code = "dataimport.commit", legacyRoles = "ROLE_DIRECTOR")
    public ImportBatchResponse commitBatch(@PathVariable Long id,
            @Valid @RequestBody(required = false) CommitImportBatchRequest request) {
        return commitImportBatchUseCase.commit(id, request != null && request.onlyValidRowsRequested());
    }

    @GetMapping("/{id}/errors")
    @RequiresPermission(code = "dataimport.read", legacyRoles = "ROLE_DIRECTOR")
    public PageResponse<ImportRowErrorResponse> listErrors(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return listImportRowErrorsUseCase.list(id, page, size);
    }

    @PostMapping("/{id}/retry")
    @RequiresPermission(code = "dataimport.commit", legacyRoles = "ROLE_DIRECTOR")
    public ImportBatchResponse retryBatch(@PathVariable Long id) {
        return retryImportBatchUseCase.retry(id);
    }

    @PostMapping("/{id}/seal")
    @RequiresPermission(code = "dataimport.seal", legacyRoles = "ROLE_DIRECTOR")
    public ImportBatchResponse sealBatch(@PathVariable Long id, @Valid @RequestBody ConfirmImportActionRequest request) {
        return sealImportBatchUseCase.seal(id, request.confirmation());
    }

    @PostMapping("/{id}/rollback")
    @RequiresPermission(code = "dataimport.rollback", legacyRoles = "ROLE_DIRECTOR")
    public ImportBatchResponse rollbackBatch(@PathVariable Long id,
            @Valid @RequestBody ConfirmImportActionRequest request) {
        return rollbackImportBatchUseCase.rollback(id, request.confirmation());
    }

    @GetMapping("/{id}/rollback-preview")
    @RequiresPermission(code = "dataimport.read", legacyRoles = "ROLE_DIRECTOR")
    public ImportRollbackPreviewResponse previewRollback(@PathVariable Long id) {
        return previewImportRollbackUseCase.preview(id);
    }

    @GetMapping("/{id}/report.csv")
    @RequiresPermission(code = "dataimport.download", legacyRoles = "ROLE_DIRECTOR")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        return csv(downloadImportReportUseCase.download(id));
    }

    @GetMapping("/templates/{entityType}")
    @RequiresPermission(code = "dataimport.download", legacyRoles = "ROLE_DIRECTOR")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable ImportEntityType entityType) {
        return csv(downloadImportTemplateUseCase.download(entityType));
    }

    private static ResponseEntity<byte[]> csv(ImportFileDownload download) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(download.content());
    }
}
