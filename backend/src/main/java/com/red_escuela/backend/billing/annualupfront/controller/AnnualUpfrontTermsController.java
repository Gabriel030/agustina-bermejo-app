package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.annualupfront.dto.AnnualUpfrontTermsResponse;
import com.red_escuela.backend.billing.annualupfront.dto.AuditedAnnualUpfrontTermsResponse;
import com.red_escuela.backend.billing.annualupfront.dto.PublishAnnualUpfrontTermsRequest;
import com.red_escuela.backend.billing.annualupfront.usecase.PublishAnnualUpfrontTermsUseCase;
import com.red_escuela.backend.billing.annualupfront.usecase.ViewAnnualUpfrontTermsUseCase;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class AnnualUpfrontTermsController {
    private final ViewAnnualUpfrontTermsUseCase viewUseCase;
    private final PublishAnnualUpfrontTermsUseCase publishUseCase;

    public AnnualUpfrontTermsController(ViewAnnualUpfrontTermsUseCase viewUseCase, PublishAnnualUpfrontTermsUseCase publishUseCase) {
        this.viewUseCase = viewUseCase;
        this.publishUseCase = publishUseCase;
    }

    @GetMapping("/annual-upfront-terms/current")
    public AnnualUpfrontTermsResponse viewCurrentTerms() {
        return AnnualUpfrontTermsResponse.from(viewUseCase.viewCurrentAnnualUpfrontTerms());
    }

    @GetMapping("/annual-upfront-terms/versions/{versionNumber}")
    public AnnualUpfrontTermsResponse viewTermsOfVersion(@PathVariable int versionNumber) {
        return AnnualUpfrontTermsResponse.from(viewUseCase.viewAnnualUpfrontTermsOfVersion(versionNumber));
    }

    @GetMapping("/annual-upfront-terms/versions")
    public List<AuditedAnnualUpfrontTermsResponse> listTermsVersions() {
        return viewUseCase.listAnnualUpfrontTermsVersions().stream().map(AuditedAnnualUpfrontTermsResponse::from).toList();
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping("/annual-upfront-terms/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public AuditedAnnualUpfrontTermsResponse publishNewTermsVersion(@Valid @RequestBody PublishAnnualUpfrontTermsRequest request) {
        return AuditedAnnualUpfrontTermsResponse.from(publishUseCase.publishNewAnnualUpfrontTermsVersion(request.text()));
    }
}
