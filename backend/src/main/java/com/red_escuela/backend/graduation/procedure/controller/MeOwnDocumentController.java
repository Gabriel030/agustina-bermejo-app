package com.red_escuela.backend.graduation.procedure.controller;

import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentType;
import com.red_escuela.backend.graduation.procedure.usecase.GenerateOwnDocumentUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/documents")
public class MeOwnDocumentController {

    private final GenerateOwnDocumentUseCase generateUseCase;

    public MeOwnDocumentController(GenerateOwnDocumentUseCase generateUseCase) {
        this.generateUseCase = generateUseCase;
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnDocumentResponse generate(@PathVariable OwnDocumentType type,
                                        @Valid @RequestBody(required = false) GenerateOwnDocumentRequest request) {
        return generateUseCase.generate(type, request);
    }
}
