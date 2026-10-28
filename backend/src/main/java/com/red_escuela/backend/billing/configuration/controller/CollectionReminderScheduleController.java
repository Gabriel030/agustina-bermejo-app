package com.red_escuela.backend.billing.configuration.controller;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.dto.ChangeReminderScheduleRequest;
import com.red_escuela.backend.billing.configuration.dto.ConfigurationChangePendingAuthorizationResponse;
import com.red_escuela.backend.billing.configuration.dto.ConfigurationChangeValidationResponse;
import com.red_escuela.backend.billing.configuration.dto.ReminderSchedulePoliciesResponse;
import com.red_escuela.backend.billing.configuration.dto.ReminderScheduleVersionResponse;
import com.red_escuela.backend.billing.configuration.dto.RemoveReminderScheduleExceptionRequest;
import com.red_escuela.backend.billing.configuration.usecase.FindConfigurationVersionAuthorNamesUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ListReminderSchedulePoliciesUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ListReminderScheduleVersionsUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ProposeReminderScheduleChangeUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ValidateReminderScheduleChangeUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/billing-configuration/collection-reminder-schedules")
@PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
public class CollectionReminderScheduleController {
    private final ListReminderSchedulePoliciesUseCase listPoliciesUseCase;
    private final ProposeReminderScheduleChangeUseCase proposeChangeUseCase;
    private final ValidateReminderScheduleChangeUseCase validateChangeUseCase;
    private final ListReminderScheduleVersionsUseCase listVersionsUseCase;
    private final FindConfigurationVersionAuthorNamesUseCase findAuthorNamesUseCase;

    public CollectionReminderScheduleController(ListReminderSchedulePoliciesUseCase listPoliciesUseCase, ProposeReminderScheduleChangeUseCase proposeChangeUseCase,
                                                ValidateReminderScheduleChangeUseCase validateChangeUseCase, ListReminderScheduleVersionsUseCase listVersionsUseCase,
                                                FindConfigurationVersionAuthorNamesUseCase findAuthorNamesUseCase) {
        this.listPoliciesUseCase = listPoliciesUseCase;
        this.proposeChangeUseCase = proposeChangeUseCase;
        this.validateChangeUseCase = validateChangeUseCase;
        this.listVersionsUseCase = listVersionsUseCase;
        this.findAuthorNamesUseCase = findAuthorNamesUseCase;
    }

    @GetMapping
    public ReminderSchedulePoliciesResponse listPolicies() {
        return ReminderSchedulePoliciesResponse.from(listPoliciesUseCase.listPolicies());
    }

    @PostMapping("/{group}/changes")
    public ResponseEntity<?> proposeGroupChange(@PathVariable TeachingGroup group, @Valid @RequestBody ChangeReminderScheduleRequest request) {
        return respond(proposeChangeUseCase.proposeChange(new ReminderScheduleTarget(group, null), request.schedule().toSchedule(), request.effectiveFrom(),
                request.changeReason()));
    }

    @PostMapping("/{group}/validation")
    public ConfigurationChangeValidationResponse validateGroupChange(@PathVariable TeachingGroup group, @Valid @RequestBody ChangeReminderScheduleRequest request) {
        return new ConfigurationChangeValidationResponse(validateChangeUseCase.validateChange(new ReminderScheduleTarget(group, null), request.schedule().toSchedule(),
                request.effectiveFrom(), request.changeReason()));
    }

    @GetMapping("/{group}/versions")
    public List<ReminderScheduleVersionResponse> listGroupVersions(@PathVariable TeachingGroup group) {
        return versionsOf(new ReminderScheduleTarget(group, null));
    }

    @PostMapping("/{group}/exceptions/{targetId}/changes")
    public ResponseEntity<?> proposeExceptionChange(@PathVariable TeachingGroup group, @PathVariable Long targetId,
                                                    @Valid @RequestBody ChangeReminderScheduleRequest request) {
        return respond(proposeChangeUseCase.proposeChange(new ReminderScheduleTarget(group, targetId), request.schedule().toSchedule(), request.effectiveFrom(),
                request.changeReason()));
    }

    @PostMapping("/{group}/exceptions/{targetId}/validation")
    public ConfigurationChangeValidationResponse validateExceptionChange(@PathVariable TeachingGroup group, @PathVariable Long targetId,
                                                                         @Valid @RequestBody ChangeReminderScheduleRequest request) {
        return new ConfigurationChangeValidationResponse(validateChangeUseCase.validateChange(new ReminderScheduleTarget(group, targetId),
                request.schedule().toSchedule(), request.effectiveFrom(), request.changeReason()));
    }

    @GetMapping("/{group}/exceptions/{targetId}/versions")
    public List<ReminderScheduleVersionResponse> listExceptionVersions(@PathVariable TeachingGroup group, @PathVariable Long targetId) {
        return versionsOf(new ReminderScheduleTarget(group, targetId));
    }

    @PostMapping("/{group}/exceptions/{targetId}/removal")
    public ResponseEntity<?> proposeExceptionRemoval(@PathVariable TeachingGroup group, @PathVariable Long targetId,
                                                     @Valid @RequestBody RemoveReminderScheduleExceptionRequest request) {
        return respond(proposeChangeUseCase.proposeExceptionRemoval(new ReminderScheduleTarget(group, targetId), request.effectiveFrom(), request.changeReason()));
    }

    private List<ReminderScheduleVersionResponse> versionsOf(ReminderScheduleTarget target) {
        List<ConfigurationVersion> versions = listVersionsUseCase.listVersions(target);
        Map<Long, String> authorNames = findAuthorNamesUseCase.findAuthorNamesOf(versions);
        return versions.stream().map(version -> ReminderScheduleVersionResponse.from(version, authorNames)).toList();
    }

    private ResponseEntity<?> respond(BillingOperationOutcome<ConfigurationVersion> outcome) {
        return switch (outcome) {
            case BillingOperationOutcome.Processed<ConfigurationVersion> processed ->
                    ResponseEntity.status(HttpStatus.CREATED).body(ReminderScheduleVersionResponse.from(processed.result(), findAuthorNamesUseCase.findAuthorNamesOf(List.of(processed.result()))));
            case BillingOperationOutcome.AwaitingAuthorization<ConfigurationVersion> awaiting ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new ConfigurationChangePendingAuthorizationResponse(awaiting.authorizationRequestId()));
        };
    }
}
