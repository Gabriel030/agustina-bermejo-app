package com.red_escuela.backend.billing.discount.controller;

import com.red_escuela.backend.billing.discount.domain.CohortPromotionStatus;
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
import com.red_escuela.backend.billing.discount.usecase.FindDiscountDefinitionUseCase;
import com.red_escuela.backend.billing.discount.usecase.ListDiscountDefinitionsUseCase;
import com.red_escuela.backend.billing.discount.usecase.ValidateDiscountDefinitionUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.shared.module.RequiresModule;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequiresModule(RequiresModule.BILLING)
@RequestMapping("/discount-definitions")
public class DiscountDefinitionController {
    private final ListDiscountDefinitionsUseCase listUseCase;
    private final FindDiscountDefinitionUseCase findUseCase;
    private final CreateDiscountDefinitionUseCase createUseCase;
    private final ValidateDiscountDefinitionUseCase validateUseCase;
    private final CloseDiscountDefinitionUseCase closeUseCase;

    public DiscountDefinitionController(ListDiscountDefinitionsUseCase listUseCase, FindDiscountDefinitionUseCase findUseCase, CreateDiscountDefinitionUseCase createUseCase,
                                        ValidateDiscountDefinitionUseCase validateUseCase, CloseDiscountDefinitionUseCase closeUseCase) {
        this.listUseCase = listUseCase;
        this.findUseCase = findUseCase;
        this.createUseCase = createUseCase;
        this.validateUseCase = validateUseCase;
        this.closeUseCase = closeUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<DiscountDefinitionCatalogEntryResponse> listDiscountDefinitions(@RequestParam(required = false) DiscountDefinitionType type,
                                                                                @RequestParam(required = false) DiscountDefinitionStatus status,
                                                                                @RequestParam(required = false) CohortPromotionStatus cohortPromotionStatus) {
        return listUseCase.listDiscountDefinitions(type, status, cohortPromotionStatus).stream().map(DiscountDefinitionCatalogEntryResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La definición con su disponibilidad, su costo estimado de cupo y, en promociones de cohorte, su estado derivado"),
            @ApiResponse(responseCode = "404", description = "No existe la definición")})
    public DiscountDefinitionCatalogEntryResponse findDiscountDefinition(@PathVariable Long id) {
        return DiscountDefinitionCatalogEntryResponse.from(findUseCase.findDiscountDefinition(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "El director publicó la promoción", content = @Content(schema = @Schema(implementation = DiscountDefinitionResponse.class))),
            @ApiResponse(responseCode = "202", description = "El administrativo dejó un pedido pendiente de aprobación", content = @Content(schema = @Schema(implementation = DiscountDefinitionPendingAuthorizationResponse.class)))})
    public ResponseEntity<?> createDiscountDefinition(@RequestBody CreateDiscountDefinitionRequest request) {
        return responseOf(createUseCase.createDiscountDefinition(request.toDraft()));
    }

    @PostMapping("/validation")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Todas las violaciones de las reglas del catálogo; lista vacía si el borrador es coherente"),
            @ApiResponse(responseCode = "400", description = "Cuerpo que no se puede interpretar: INVALID_PERCENTAGE (porcentaje fuera de 0 a 100), NEGATIVE_ADJUSTMENT_AMOUNT (monto fijo negativo), UNSUPPORTED_CURRENCY. Estos no vuelven como violaciones porque el valor no llega a construirse"),
            @ApiResponse(responseCode = "404", description = "La carrera, el curso o la cohorte del alcance no existen")})
    public DiscountDefinitionValidationResponse validateDiscountDefinition(@RequestBody CreateDiscountDefinitionRequest request) {
        return new DiscountDefinitionValidationResponse(validateUseCase.validateDiscountDefinition(request.toDraft()));
    }

    @PostMapping("/{id}/closure")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "El director cerró la promoción", content = @Content(schema = @Schema(implementation = DiscountDefinitionResponse.class))),
            @ApiResponse(responseCode = "202", description = "El administrativo dejó un pedido de cierre pendiente de aprobación", content = @Content(schema = @Schema(implementation = DiscountDefinitionPendingAuthorizationResponse.class)))})
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
