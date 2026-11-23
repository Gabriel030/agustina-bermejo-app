package com.red_escuela.backend.graduation.certificate.template.controller;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageUpload;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.dto.PreviewCertificateTemplateRequest;
import com.red_escuela.backend.graduation.certificate.template.dto.SaveCertificateTemplateDraftRequest;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateImagePurpose;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.graduation.certificate.template.usecase.DiscardDraftDocumentTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.GetDocumentTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.GetImageDocumentTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.GetVersionDocumentTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.ListVersionsDocumentTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.PreviewDocumentTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.PublishDocumentTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.SaveDraftDocumentTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.UploadImageDocumentTemplateUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/certificate-templates/documents/{documentType}")
public class DocumentCertificateTemplateController {

    private final GetDocumentTemplateUseCase getUseCase;
    private final ListVersionsDocumentTemplateUseCase listVersionsUseCase;
    private final GetVersionDocumentTemplateUseCase getVersionUseCase;
    private final SaveDraftDocumentTemplateUseCase saveDraftUseCase;
    private final DiscardDraftDocumentTemplateUseCase discardDraftUseCase;
    private final PublishDocumentTemplateUseCase publishUseCase;
    private final UploadImageDocumentTemplateUseCase uploadImageUseCase;
    private final GetImageDocumentTemplateUseCase getImageUseCase;
    private final PreviewDocumentTemplateUseCase previewUseCase;

    public DocumentCertificateTemplateController(GetDocumentTemplateUseCase getUseCase,
                                                 ListVersionsDocumentTemplateUseCase listVersionsUseCase,
                                                 GetVersionDocumentTemplateUseCase getVersionUseCase,
                                                 SaveDraftDocumentTemplateUseCase saveDraftUseCase,
                                                 DiscardDraftDocumentTemplateUseCase discardDraftUseCase,
                                                 PublishDocumentTemplateUseCase publishUseCase,
                                                 UploadImageDocumentTemplateUseCase uploadImageUseCase,
                                                 GetImageDocumentTemplateUseCase getImageUseCase,
                                                 PreviewDocumentTemplateUseCase previewUseCase) {
        this.getUseCase = getUseCase;
        this.listVersionsUseCase = listVersionsUseCase;
        this.getVersionUseCase = getVersionUseCase;
        this.saveDraftUseCase = saveDraftUseCase;
        this.discardDraftUseCase = discardDraftUseCase;
        this.publishUseCase = publishUseCase;
        this.uploadImageUseCase = uploadImageUseCase;
        this.getImageUseCase = getImageUseCase;
        this.previewUseCase = previewUseCase;
    }

    @GetMapping
    @RequiresPermission(code = "certificate.templates_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public DocumentTemplateResponse get(@PathVariable DocumentTemplateType documentType) {
        return getUseCase.get(documentType);
    }

    @GetMapping("/versions")
    @RequiresPermission(code = "certificate.templates_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<CertificateTemplateVersionSummaryResponse> versions(@PathVariable DocumentTemplateType documentType) {
        return listVersionsUseCase.list(documentType);
    }

    @GetMapping("/versions/{number}")
    @RequiresPermission(code = "certificate.templates_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CertificateTemplateVersionResponse version(@PathVariable DocumentTemplateType documentType, @PathVariable int number) {
        return getVersionUseCase.get(documentType, number);
    }

    @PutMapping("/draft")
    @RequiresPermission(code = "certificate.templates_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateTemplateVersionResponse saveDraft(@PathVariable DocumentTemplateType documentType,
                                                        @Valid @RequestBody SaveCertificateTemplateDraftRequest request) {
        return saveDraftUseCase.save(documentType, request.content());
    }

    @DeleteMapping("/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "certificate.templates_manage", legacyRoles = "ROLE_DIRECTOR")
    public void discardDraft(@PathVariable DocumentTemplateType documentType) {
        discardDraftUseCase.discard(documentType);
    }

    @PostMapping("/publish")
    @RequiresPermission(code = "certificate.templates_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateTemplateVersionResponse publish(@PathVariable DocumentTemplateType documentType) {
        return publishUseCase.publish(documentType);
    }

    @Operation(description = "Solo PNG, hasta 2 MB (se valida por magic bytes)")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "certificate.templates_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateTemplateImageResponse uploadImage(@PathVariable DocumentTemplateType documentType,
                                                        @RequestParam TemplateImagePurpose purpose,
                                                        @RequestPart("file") MultipartFile file) throws IOException {
        return uploadImageUseCase.upload(documentType,
                new CertificateTemplateImageUpload(purpose, file.getBytes(), file.getContentType(), file.getOriginalFilename()));
    }

    @GetMapping("/images/{id}")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")))
    @RequiresPermission(code = "certificate.templates_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ResponseEntity<byte[]> image(@PathVariable DocumentTemplateType documentType, @PathVariable Long id) {
        CertificateTemplateImageContent image = getImageUseCase.get(documentType, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.content());
    }

    @PostMapping("/preview")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @PreAuthorize("hasRole('DIRECTOR') or (hasRole('ADMINISTRATIVO') and #request.source().name() == 'PUBLISHED')")
    public ResponseEntity<byte[]> preview(@PathVariable DocumentTemplateType documentType,
                                          @Valid @RequestBody PreviewCertificateTemplateRequest request) {
        GraduationCertificatePdf pdf = previewUseCase.preview(documentType, request.source());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("vista-previa-plantilla.pdf").build().toString())
                .body(pdf.content());
    }
}
