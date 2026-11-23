package com.red_escuela.backend.graduation.certificate.signatory.controller;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryFilter;
import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatorySummaryResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SaveCertificateSignatoryRequest;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureContent;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureUpload;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.ActivateCertificateSignatoryUseCase;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.CreateCertificateSignatoryUseCase;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.DeactivateCertificateSignatoryUseCase;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.DeleteCertificateSignatorySignatureUseCase;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.GetCertificateSignatorySignatureUseCase;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.GetCertificateSignatoryUseCase;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.ListCertificateSignatoriesUseCase;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.UpdateCertificateSignatoryUseCase;
import com.red_escuela.backend.graduation.certificate.signatory.usecase.UploadCertificateSignatorySignatureUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.io.IOException;

@RestController
@RequestMapping("/certificate-signatories")
public class CertificateSignatoryController {

    private final ListCertificateSignatoriesUseCase listUseCase;
    private final GetCertificateSignatoryUseCase getUseCase;
    private final CreateCertificateSignatoryUseCase createUseCase;
    private final UpdateCertificateSignatoryUseCase updateUseCase;
    private final DeactivateCertificateSignatoryUseCase deactivateUseCase;
    private final ActivateCertificateSignatoryUseCase activateUseCase;
    private final UploadCertificateSignatorySignatureUseCase uploadSignatureUseCase;
    private final DeleteCertificateSignatorySignatureUseCase deleteSignatureUseCase;
    private final GetCertificateSignatorySignatureUseCase getSignatureUseCase;

    public CertificateSignatoryController(ListCertificateSignatoriesUseCase listUseCase,
                                          GetCertificateSignatoryUseCase getUseCase,
                                          CreateCertificateSignatoryUseCase createUseCase,
                                          UpdateCertificateSignatoryUseCase updateUseCase,
                                          DeactivateCertificateSignatoryUseCase deactivateUseCase,
                                          ActivateCertificateSignatoryUseCase activateUseCase,
                                          UploadCertificateSignatorySignatureUseCase uploadSignatureUseCase,
                                          DeleteCertificateSignatorySignatureUseCase deleteSignatureUseCase,
                                          GetCertificateSignatorySignatureUseCase getSignatureUseCase) {
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deactivateUseCase = deactivateUseCase;
        this.activateUseCase = activateUseCase;
        this.uploadSignatureUseCase = uploadSignatureUseCase;
        this.deleteSignatureUseCase = deleteSignatureUseCase;
        this.getSignatureUseCase = getSignatureUseCase;
    }

    @GetMapping
    @RequiresPermission(code = "certificate.signers_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public Page<CertificateSignatorySummaryResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SignatoryDocumentType documentType,
            @PageableDefault(size = 25) Pageable pageable) {
        return listUseCase.list(new CertificateSignatoryFilter(active, q, documentType), pageable);
    }

    @GetMapping("/{id}")
    @RequiresPermission(code = "certificate.signers_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CertificateSignatoryDetailResponse get(@PathVariable Long id) {
        return getUseCase.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "certificate.signers_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateSignatoryDetailResponse create(@Valid @RequestBody SaveCertificateSignatoryRequest request) {
        return createUseCase.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(code = "certificate.signers_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateSignatoryDetailResponse update(@PathVariable Long id,
                                                     @Valid @RequestBody SaveCertificateSignatoryRequest request) {
        return updateUseCase.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @RequiresPermission(code = "certificate.signers_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateSignatoryDetailResponse deactivate(@PathVariable Long id) {
        return deactivateUseCase.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @RequiresPermission(code = "certificate.signers_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateSignatoryDetailResponse activate(@PathVariable Long id) {
        return activateUseCase.activate(id);
    }

    @Operation(description = "Solo PNG, hasta 2 MB (se valida por magic bytes); la imagen no se guarda en la auditoría")
    @PutMapping(value = "/{id}/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(code = "certificate.signers_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateSignatoryDetailResponse uploadSignature(@PathVariable Long id,
                                                              @RequestPart("file") MultipartFile file) throws IOException {
        return uploadSignatureUseCase.upload(id,
                new SignatorySignatureUpload(file.getBytes(), file.getContentType(), file.getOriginalFilename()));
    }

    @DeleteMapping("/{id}/signature")
    @RequiresPermission(code = "certificate.signers_manage", legacyRoles = "ROLE_DIRECTOR")
    public CertificateSignatoryDetailResponse deleteSignature(@PathVariable Long id) {
        return deleteSignatureUseCase.delete(id);
    }

    @GetMapping("/{id}/signature")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")))
    @RequiresPermission(code = "certificate.signers_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ResponseEntity<byte[]> signature(@PathVariable Long id) {
        SignatorySignatureContent signature = getSignatureUseCase.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(signature.contentType()))
                .body(signature.content());
    }
}
