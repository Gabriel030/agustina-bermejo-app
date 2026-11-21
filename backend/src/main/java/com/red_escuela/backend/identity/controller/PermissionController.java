package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.PermissionCatalogResponse;
import com.red_escuela.backend.identity.dto.PermissionDiscrepancyResponse;
import com.red_escuela.backend.identity.dto.PermissionDiscrepancySummaryResponse;
import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.dto.UpdatePermissionGridRequest;
import com.red_escuela.backend.identity.dto.UpdatePermissionModesRequest;
import com.red_escuela.backend.identity.usecase.permission.GetPermissionCatalogUseCase;
import com.red_escuela.backend.identity.usecase.permission.GetPermissionDiscrepancySummaryUseCase;
import com.red_escuela.backend.identity.usecase.permission.GetPermissionGridUseCase;
import com.red_escuela.backend.identity.usecase.permission.ListPermissionDiscrepanciesUseCase;
import com.red_escuela.backend.identity.usecase.permission.UpdatePermissionGridUseCase;
import com.red_escuela.backend.identity.usecase.permission.UpdatePermissionModesUseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final GetPermissionCatalogUseCase getPermissionCatalogUseCase;
    private final GetPermissionGridUseCase getPermissionGridUseCase;
    private final UpdatePermissionGridUseCase updatePermissionGridUseCase;
    private final ListPermissionDiscrepanciesUseCase listPermissionDiscrepanciesUseCase;
    private final UpdatePermissionModesUseCase updatePermissionModesUseCase;
    private final GetPermissionDiscrepancySummaryUseCase getPermissionDiscrepancySummaryUseCase;

    public PermissionController(GetPermissionCatalogUseCase getPermissionCatalogUseCase,
                                GetPermissionGridUseCase getPermissionGridUseCase,
                                UpdatePermissionGridUseCase updatePermissionGridUseCase,
                                ListPermissionDiscrepanciesUseCase listPermissionDiscrepanciesUseCase,
                                UpdatePermissionModesUseCase updatePermissionModesUseCase,
                                GetPermissionDiscrepancySummaryUseCase getPermissionDiscrepancySummaryUseCase) {
        this.getPermissionCatalogUseCase = getPermissionCatalogUseCase;
        this.getPermissionGridUseCase = getPermissionGridUseCase;
        this.updatePermissionGridUseCase = updatePermissionGridUseCase;
        this.listPermissionDiscrepanciesUseCase = listPermissionDiscrepanciesUseCase;
        this.updatePermissionModesUseCase = updatePermissionModesUseCase;
        this.getPermissionDiscrepancySummaryUseCase = getPermissionDiscrepancySummaryUseCase;
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PermissionCatalogResponse getCatalog() {
        return getPermissionCatalogUseCase.get();
    }

    @GetMapping("/grid")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PermissionGridResponse getGrid() {
        return getPermissionGridUseCase.get();
    }

    @PutMapping("/grid")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PermissionGridResponse updateGrid(@Valid @RequestBody UpdatePermissionGridRequest request) {
        return updatePermissionGridUseCase.update(request);
    }

    @GetMapping("/discrepancies")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PageResponse<PermissionDiscrepancyResponse> listDiscrepancies(@RequestParam(required = false) String module,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "50") int size) {
        return listPermissionDiscrepanciesUseCase.list(module, page, size);
    }

    @GetMapping("/discrepancies/summary")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PermissionDiscrepancySummaryResponse getDiscrepancySummary() {
        return getPermissionDiscrepancySummaryUseCase.get();
    }

    @PutMapping("/modes")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PermissionGridResponse updateModes(@Valid @RequestBody UpdatePermissionModesRequest request) {
        return updatePermissionModesUseCase.update(request);
    }
}
