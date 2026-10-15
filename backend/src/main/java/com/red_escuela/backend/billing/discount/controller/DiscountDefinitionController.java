package com.red_escuela.backend.billing.discount.controller;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.dto.CloseDiscountDefinitionRequest;
import com.red_escuela.backend.billing.discount.dto.CreateDiscountDefinitionRequest;
import com.red_escuela.backend.billing.discount.dto.DiscountDefinitionCatalogEntryResponse;
import com.red_escuela.backend.billing.discount.dto.DiscountDefinitionPendingAuthorizationResponse;
import com.red_escuela.backend.billing.discount.dto.DiscountDefinitionResponse;
import com.red_escuela.backend.billing.discount.dto.DiscountDefinitionValidationResponse;
import com.red_escuela.backend.billing.discount.usecase.CloseDiscountDefinitionUseCase;
import com.red_escuela.backend.billing.discount.usecase.CreateDiscountDefinitionUseCase;
import com.red_escuela.backend.billing.discount.usecase.ListDiscountDefinitionsUseCase;
import com.red_escuela.backend.billing.discount.usecase.ValidateDiscountDefinitionUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/discount-definitions")
public class DiscountDefinitionController {
    private final ListDiscountDefinitionsUseCase listUseCase;
    private final CreateDiscountDefinitionUseCase createUseCase;
    private final ValidateDiscountDefinitionUseCase validateUseCase;
    private final CloseDiscountDefinitionUseCase closeUseCase;

    public DiscountDefinitionController(ListDiscountDefinitionsUseCase listUseCase, CreateDiscountDefinitionUseCase createUseCase,
                                        ValidateDiscountDefinitionUseCase validateUseCase, CloseDiscountDefinitionUseCase closeUseCase) {
        this.listUseCase = listUseCase;
        this.createUseCase = createUseCase;
        this.validateUseCase = validateUseCase;
        this.closeUseCase = closeUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<DiscountDefinitionCatalogEntryResponse> listDiscountDefinitions(@RequestParam(required = false) DiscountDefinitionType type,
                                                                                @RequestParam(required = false) DiscountDefinitionStatus status) {
        return listUseCase.listDiscountDefinitions(type, status).stream().map(DiscountDefinitionCatalogEntryResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public ResponseEntity<?> createDiscountDefinition(@RequestBody CreateDiscountDefinitionRequest request) {
        return responseOf(createUseCase.createDiscountDefinition(request.toDraft()));
    }

    @PostMapping("/validation")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public DiscountDefinitionValidationResponse validateDiscountDefinition(@RequestBody CreateDiscountDefinitionRequest request) {
        return new DiscountDefinitionValidationResponse(validateUseCase.validateDiscountDefinition(request.toDraft()));
    }

    @PostMapping("/{id}/closure")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public ResponseEntity<?> closeDiscountDefinition(@PathVariable Long id, @RequestBody CloseDiscountDefinitionRequest request) {
        return responseOf(closeUseCase.closeDiscountDefinition(id, request.reason()));
    }

    private ResponseEntity<?> responseOf(BillingOperationOutcome<DiscountDefinition> outcome) {
        return switch (outcome) {
            case BillingOperationOutcome.Processed<DiscountDefinition> processed ->
                    ResponseEntity.status(HttpStatus.CREATED).body(DiscountDefinitionResponse.from(processed.result()));
            case BillingOperationOutcome.AwaitingAuthorization<DiscountDefinition> awaiting ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new DiscountDefinitionPendingAuthorizationResponse(awaiting.authorizationRequestId()));
        };
    }
}
