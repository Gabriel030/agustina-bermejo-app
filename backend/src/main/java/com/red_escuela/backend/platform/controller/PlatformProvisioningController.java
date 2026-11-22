package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.domain.FirstDirectorProvisioningCommand;
import com.red_escuela.backend.platform.dto.FirstDirectorProvisioningResponse;
import com.red_escuela.backend.platform.dto.FirstDirectorStatusResponse;
import com.red_escuela.backend.platform.dto.ProvisionFirstDirectorRequest;
import com.red_escuela.backend.platform.usecase.FindFirstDirectorStatusUseCase;
import com.red_escuela.backend.platform.usecase.ProvisionFirstDirectorUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformProvisioningController {

    private final ProvisionFirstDirectorUseCase provisionFirstDirector;
    private final FindFirstDirectorStatusUseCase findFirstDirectorStatus;

    public PlatformProvisioningController(
            ProvisionFirstDirectorUseCase provisionFirstDirector, FindFirstDirectorStatusUseCase findFirstDirectorStatus) {
        this.provisionFirstDirector = provisionFirstDirector;
        this.findFirstDirectorStatus = findFirstDirectorStatus;
    }

    @GetMapping("/platform/provisioning/first-director/status")
    public FirstDirectorStatusResponse findFirstDirectorStatus() {
        return FirstDirectorStatusResponse.of(findFirstDirectorStatus.findStatus());
    }

    @PostMapping("/platform/provisioning/first-director")
    @ResponseStatus(HttpStatus.CREATED)
    public FirstDirectorProvisioningResponse provisionFirstDirector(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProvisionFirstDirectorRequest request) {
        return FirstDirectorProvisioningResponse.of(provisionFirstDirector.provision(new FirstDirectorProvisioningCommand(
                request.institutionName(), request.firstName(), request.lastName(), request.email(),
                request.documentType(), request.documentNumber(), request.reason(), idempotencyKey)));
    }
}
