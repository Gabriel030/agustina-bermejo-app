package com.red_escuela.backend.graduation.certificate.verification.controller;

import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import com.red_escuela.backend.graduation.certificate.verification.usecase.VerifyPublicDocumentUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/public/documents")
public class PublicDocumentController {

    private final VerifyPublicDocumentUseCase verifyUseCase;

    public PublicDocumentController(VerifyPublicDocumentUseCase verifyUseCase) {
        this.verifyUseCase = verifyUseCase;
    }

    @GetMapping("/{code}")
    public ResponseEntity<PublicDocumentVerificationResponse> verify(@PathVariable String code, HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(verifyUseCase.verify(code, request.getRemoteAddr()));
    }
}
