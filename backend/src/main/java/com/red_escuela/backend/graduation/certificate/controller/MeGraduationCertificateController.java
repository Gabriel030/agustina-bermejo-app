package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.dto.OwnGraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.PdfDisposition;
import com.red_escuela.backend.graduation.certificate.usecase.GetOwnGraduationCertificatePdfUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.ListOwnGraduationCertificatesUseCase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me/certificates")
public class MeGraduationCertificateController {

    private final ListOwnGraduationCertificatesUseCase listUseCase;
    private final GetOwnGraduationCertificatePdfUseCase pdfUseCase;

    public MeGraduationCertificateController(ListOwnGraduationCertificatesUseCase listUseCase,
                                             GetOwnGraduationCertificatePdfUseCase pdfUseCase) {
        this.listUseCase = listUseCase;
        this.pdfUseCase = pdfUseCase;
    }

    @GetMapping
    public List<OwnGraduationCertificateResponse> list() {
        return listUseCase.listOwn();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id,
                                      @RequestParam(defaultValue = "ATTACHMENT") PdfDisposition disposition) {
        GraduationCertificatePdf pdf = pdfUseCase.pdfOf(id);
        String filename = "certificado-egreso-" + pdf.code() + ".pdf";
        ContentDisposition contentDisposition = disposition == PdfDisposition.INLINE
                ? ContentDisposition.inline().filename(filename).build()
                : ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(pdf.content());
    }
}
