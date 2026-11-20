package com.red_escuela.backend.graduation.certificate.regular.controller;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSubmissionResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.StudentRegularCertificateStateResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.SubmitRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.usecase.CancelStudentRegularRequestUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetStudentRegularCertificatePdfUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetStudentRegularCertificateStateUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetStudentRegularityUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.SubmitRegularCertificateUseCase;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeRegularCertificateController {

    private final SubmitRegularCertificateUseCase submitUseCase;
    private final GetStudentRegularCertificateStateUseCase stateUseCase;
    private final GetStudentRegularCertificatePdfUseCase pdfUseCase;
    private final CancelStudentRegularRequestUseCase cancelUseCase;
    private final GetStudentRegularityUseCase regularityUseCase;

    public MeRegularCertificateController(SubmitRegularCertificateUseCase submitUseCase,
                                          GetStudentRegularCertificateStateUseCase stateUseCase,
                                          GetStudentRegularCertificatePdfUseCase pdfUseCase,
                                          CancelStudentRegularRequestUseCase cancelUseCase,
                                          GetStudentRegularityUseCase regularityUseCase) {
        this.regularityUseCase = regularityUseCase;
        this.submitUseCase = submitUseCase;
        this.stateUseCase = stateUseCase;
        this.pdfUseCase = pdfUseCase;
        this.cancelUseCase = cancelUseCase;
    }

    @PostMapping("/me/regular-certificates")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Constancia emitida en el acto",
                    content = @Content(schema = @Schema(implementation = RegularCertificateSubmissionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Pedido pendiente de confirmación de administración",
                    content = @Content(schema = @Schema(implementation = RegularCertificateSubmissionResponse.class)))})
    public ResponseEntity<RegularCertificateSubmissionResponse> submit(@Valid @RequestBody SubmitRegularCertificateRequest request) {
        RegularCertificateSubmissionResponse response = submitUseCase.submit(request);
        return ResponseEntity.status(response.wasIssued() ? HttpStatus.CREATED : HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/me/regular-certificates")
    public StudentRegularCertificateStateResponse state(@RequestParam RegularityKind kind, @RequestParam Long enrollmentId) {
        return stateUseCase.stateOf(kind, enrollmentId);
    }

    @GetMapping("/me/regular-certificates/eligibility")
    public RegularityResponse regularity(@RequestParam RegularityKind kind, @RequestParam Long enrollmentId) {
        return regularityUseCase.regularityOf(kind, enrollmentId);
    }

    @GetMapping("/me/regular-certificates/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        RegularCertificatePdf pdf = pdfUseCase.pdfOf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("constancia-regular-" + pdf.code() + ".pdf").build().toString())
                .body(pdf.content());
    }

    @DeleteMapping("/me/regular-certificate-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        cancelUseCase.cancel(id);
    }
}
