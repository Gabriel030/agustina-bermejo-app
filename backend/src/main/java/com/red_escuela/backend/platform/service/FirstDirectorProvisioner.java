package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.identity.api.CreatedFirstDirector;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.FirstDirectorCreator;
import com.red_escuela.backend.identity.api.InstitutionProvisioner;
import com.red_escuela.backend.identity.api.PasswordResetCodeIssuer;
import com.red_escuela.backend.identity.api.ProvisionedInstitution;
import com.red_escuela.backend.platform.domain.FirstDirectorProvisioning;
import com.red_escuela.backend.platform.domain.FirstDirectorProvisioningCommand;
import com.red_escuela.backend.platform.entity.PlatformProvisioningLog;
import com.red_escuela.backend.platform.exception.IdempotencyKeyReusedException;
import com.red_escuela.backend.platform.repository.PlatformProvisioningLogRepository;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class FirstDirectorProvisioner {

    private final PlatformProvisioningLogRepository provisioningLogRepository;
    private final FeatureChangeReasonValidator reasonValidator;
    private final ProvisioningRequestFingerprint requestFingerprint;
    private final CurrentActorFinder currentActorFinder;
    private final FirstDirectorCreator firstDirectorCreator;
    private final InstitutionProvisioner institutionProvisioner;
    private final PasswordResetCodeIssuer passwordResetCodeIssuer;
    private final Clock clock;

    public FirstDirectorProvisioner(
            PlatformProvisioningLogRepository provisioningLogRepository,
            FeatureChangeReasonValidator reasonValidator,
            ProvisioningRequestFingerprint requestFingerprint,
            CurrentActorFinder currentActorFinder,
            FirstDirectorCreator firstDirectorCreator,
            InstitutionProvisioner institutionProvisioner,
            PasswordResetCodeIssuer passwordResetCodeIssuer,
            Clock clock) {
        this.provisioningLogRepository = provisioningLogRepository;
        this.reasonValidator = reasonValidator;
        this.requestFingerprint = requestFingerprint;
        this.currentActorFinder = currentActorFinder;
        this.firstDirectorCreator = firstDirectorCreator;
        this.institutionProvisioner = institutionProvisioner;
        this.passwordResetCodeIssuer = passwordResetCodeIssuer;
        this.clock = clock;
    }

    @Transactional
    public FirstDirectorProvisioning provisionFirstDirector(FirstDirectorProvisioningCommand command) {
        String reason = reasonValidator.requireValidReason(command.reason());
        String requestHash = requestFingerprint.of(command);
        Optional<FirstDirectorProvisioning> replay = findPreviousProvisioning(command, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        CreatedFirstDirector director = firstDirectorCreator.createFirstDirector(
                command.firstName().trim(), command.lastName().trim(), command.email().trim(), command.documentType(), command.documentNumber().trim());
        ProvisionedInstitution institution = institutionProvisioner.provisionSingleInstitution(command.institutionName().trim());
        passwordResetCodeIssuer.issuePasswordResetCode(director.email());
        provisioningLogRepository.save(PlatformProvisioningLog.builder()
                .actorPersonId(currentActorFinder.currentPersonId())
                .occurredAt(LocalDateTime.now(clock))
                .reason(reason)
                .institutionId(institution.institutionId())
                .institutionCreated(institution.created())
                .directorPersonId(director.personId())
                .idempotencyKey(command.idempotencyKey())
                .requestHash(requestHash)
                .build());
        return new FirstDirectorProvisioning(institution.institutionId(), institution.created(), director.personId(), director.email());
    }

    private Optional<FirstDirectorProvisioning> findPreviousProvisioning(FirstDirectorProvisioningCommand command, String requestHash) {
        if (command.idempotencyKey() == null) {
            return Optional.empty();
        }
        return provisioningLogRepository.findByIdempotencyKey(command.idempotencyKey()).map(previous -> {
            if (!previous.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            return new FirstDirectorProvisioning(
                    previous.getInstitutionId(), previous.isInstitutionCreated(), previous.getDirectorPersonId(), EmailAddress.of(command.email()).value());
        });
    }
}
