package com.red_escuela.backend.graduation.certificate.template.controller;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageUpload;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.PreviewCertificateTemplateRequest;
import com.red_escuela.backend.graduation.certificate.template.dto.SaveCertificateTemplateDraftRequest;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateImagePurpose;
import com.red_escuela.backend.graduation.certificate.template.usecase.GetCertificateTemplateImageUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.GetCertificateTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.GetCertificateTemplateVersionUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.ListCertificateTemplateVersionsUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.PreviewCertificateTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.PublishCertificateTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.SaveCertificateTemplateDraftUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.UploadCertificateTemplateImageUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/certificate-templates/{kind}")
public class CertificateTemplateController {

    private final GetCertificateTemplateUseCase getUseCase;
    private final ListCertificateTemplateVersionsUseCase listVersionsUseCase;
    private final GetCertificateTemplateVersionUseCase getVersionUseCase;
    private final SaveCertificateTemplateDraftUseCase saveDraftUseCase;
    private final PublishCertificateTemplateUseCase publishUseCase;
    private final UploadCertificateTemplateImageUseCase uploadImageUseCase;
    private final GetCertificateTemplateImageUseCase getImageUseCase;
    private final PreviewCertificateTemplateUseCase previewUseCase;

    public CertificateTemplateController(GetCertificateTemplateUseCase getUseCase,
                                         ListCertificateTemplateVersionsUseCase listVersionsUseCase,
                                         GetCertificateTemplateVersionUseCase getVersionUseCase,
                                         SaveCertificateTemplateDraftUseCase saveDraftUseCase,
                                         PublishCertificateTemplateUseCase publishUseCase,
                                         UploadCertificateTemplateImageUseCase uploadImageUseCase,
                                         GetCertificateTemplateImageUseCase getImageUseCase,
                                         PreviewCertificateTemplateUseCase previewUseCase) {
        this.getUseCase = getUseCase;
        this.listVersionsUseCase = listVersionsUseCase;
        this.getVersionUseCase = getVersionUseCase;
        this.saveDraftUseCase = saveDraftUseCase;
        this.publishUseCase = publishUseCase;
        this.uploadImageUseCase = uploadImageUseCase;
        this.getImageUseCase = getImageUseCase;
        this.previewUseCase = previewUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public CertificateTemplateResponse get(@PathVariable GraduationCertificateKind kind) {
        return getUseCase.get(kind);
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public List<CertificateTemplateVersionSummaryResponse> versions(@PathVariable GraduationCertificateKind kind) {
        return listVersionsUseCase.list(kind);
    }

    @GetMapping("/versions/{number}")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public CertificateTemplateVersionResponse version(@PathVariable GraduationCertificateKind kind, @PathVariable int number) {
        return getVersionUseCase.get(kind, number);
    }

    @PutMapping("/draft")
    @PreAuthorize("hasRole('DIRECTOR')")
    public CertificateTemplateVersionResponse saveDraft(@PathVariable GraduationCertificateKind kind,
                                                        @Valid @RequestBody SaveCertificateTemplateDraftRequest request) {
        return saveDraftUseCase.save(kind, request.content());
    }

    @PostMapping("/publish")
    @PreAuthorize("hasRole('DIRECTOR')")
    public CertificateTemplateVersionResponse publish(@PathVariable GraduationCertificateKind kind) {
        return publishUseCase.publish(kind);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public CertificateTemplateImageResponse uploadImage(@PathVariable GraduationCertificateKind kind,
                                                        @RequestParam TemplateImagePurpose purpose,
                                                        @RequestPart("file") MultipartFile file) throws IOException {
        return uploadImageUseCase.upload(kind,
                new CertificateTemplateImageUpload(purpose, file.getBytes(), file.getContentType(), file.getOriginalFilename()));
    }

    @GetMapping("/images/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public ResponseEntity<byte[]> image(@PathVariable GraduationCertificateKind kind, @PathVariable Long id) {
        CertificateTemplateImageContent image = getImageUseCase.get(kind, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.content());
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('DIRECTOR') or (hasRole('ADMINISTRATIVO') and #request.source().name() == 'PUBLISHED')")
    public ResponseEntity<byte[]> preview(@PathVariable GraduationCertificateKind kind,
                                          @Valid @RequestBody PreviewCertificateTemplateRequest request) {
        GraduationCertificatePdf pdf = previewUseCase.preview(kind, request.source());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("vista-previa-plantilla.pdf").build().toString())
                .body(pdf.content());
    }
}
