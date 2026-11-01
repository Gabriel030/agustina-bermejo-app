package com.red_escuela.backend.billing.configuration.controller;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.dto.ChangeCollectionReminderSettingsRequest;
import com.red_escuela.backend.billing.configuration.dto.CollectionReminderSettingsVersionResponse;
import com.red_escuela.backend.billing.configuration.dto.ConfigurationChangePendingAuthorizationResponse;
import com.red_escuela.backend.billing.configuration.dto.ConfigurationChangeValidationResponse;
import com.red_escuela.backend.billing.configuration.usecase.FindConfigurationVersionAuthorNamesUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ListConfigurationVersionHistoryUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ValidateBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.shared.module.RequiresModule;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiresModule(RequiresModule.BILLING)
@RequestMapping("/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS")
public class CollectionReminderSettingsController {
    private static final ConfigurationTopic TOPIC = ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS);

    private final ProposeBillingConfigurationChangeUseCase proposeChangeUseCase;
    private final ValidateBillingConfigurationChangeUseCase validateChangeUseCase;
    private final ListConfigurationVersionHistoryUseCase listVersionHistoryUseCase;
    private final FindConfigurationVersionAuthorNamesUseCase findAuthorNamesUseCase;

    public CollectionReminderSettingsController(ProposeBillingConfigurationChangeUseCase proposeChangeUseCase,
                                                ValidateBillingConfigurationChangeUseCase validateChangeUseCase,
                                                ListConfigurationVersionHistoryUseCase listVersionHistoryUseCase,
                                                FindConfigurationVersionAuthorNamesUseCase findAuthorNamesUseCase) {
        this.proposeChangeUseCase = proposeChangeUseCase;
        this.validateChangeUseCase = validateChangeUseCase;
        this.listVersionHistoryUseCase = listVersionHistoryUseCase;
        this.findAuthorNamesUseCase = findAuthorNamesUseCase;
    }

    @PostMapping("/changes")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public ResponseEntity<?> proposeOrPublishChange(@Valid @RequestBody ChangeCollectionReminderSettingsRequest request) {
        BillingOperationOutcome<ConfigurationVersion> outcome = proposeChangeUseCase.proposeChange(TOPIC, request.values().toSettings(),
                request.effectiveFrom(), request.changeReason());
        return switch (outcome) {
            case BillingOperationOutcome.Processed<ConfigurationVersion> processed ->
                    ResponseEntity.status(HttpStatus.CREATED).body(CollectionReminderSettingsVersionResponse.from(processed.result(),
                            findAuthorNamesUseCase.findAuthorNamesOf(List.of(processed.result()))));
            case BillingOperationOutcome.AwaitingAuthorization<ConfigurationVersion> awaiting ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new ConfigurationChangePendingAuthorizationResponse(awaiting.authorizationRequestId()));
        };
    }

    @PostMapping("/validation")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public ConfigurationChangeValidationResponse validateChange(@Valid @RequestBody ChangeCollectionReminderSettingsRequest request) {
        return new ConfigurationChangeValidationResponse(validateChangeUseCase.validateChange(TOPIC, request.values().toSettings(),
                request.effectiveFrom(), request.changeReason()));
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<CollectionReminderSettingsVersionResponse> listVersions() {
        List<ConfigurationVersion> versions = listVersionHistoryUseCase.listVersionHistory(TOPIC);
        Map<Long, String> authorNames = findAuthorNamesUseCase.findAuthorNamesOf(versions);
        return versions.stream().map(version -> CollectionReminderSettingsVersionResponse.from(version, authorNames)).toList();
    }
}
