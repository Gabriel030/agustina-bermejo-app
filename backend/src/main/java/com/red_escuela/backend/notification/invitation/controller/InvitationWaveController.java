package com.red_escuela.backend.notification.invitation.controller;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.dto.CreateInvitationWaveCommand;
import com.red_escuela.backend.notification.invitation.dto.InvitationImportBatchEntry;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveRecipientEntry;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveReportDownload;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveSettingsResponse;
import com.red_escuela.backend.notification.invitation.dto.RetryFailedInvitationsResponse;
import com.red_escuela.backend.notification.invitation.usecase.CancelInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.CreateInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.DownloadInvitationWaveReportUseCase;
import com.red_escuela.backend.notification.invitation.usecase.GetInvitationWaveSettingsUseCase;
import com.red_escuela.backend.notification.invitation.usecase.GetInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.ListInvitationImportBatchesUseCase;
import com.red_escuela.backend.notification.invitation.usecase.ListInvitationWaveRecipientsUseCase;
import com.red_escuela.backend.notification.invitation.usecase.ListInvitationWavesUseCase;
import com.red_escuela.backend.notification.invitation.usecase.PauseInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.RetryFailedInvitationsUseCase;
import com.red_escuela.backend.notification.invitation.usecase.ResumeInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.StartInvitationWaveUseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.permission.PermissionGate;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/invitation-waves")
public class InvitationWaveController {

    private static final String LEGACY_ROLES = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR";
    private static final String READ_STUDENTS_PERMISSION = "identity.read_students";
    private static final String READ_STUDENTS_LEGACY_ROLES = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR";

    private final CreateInvitationWaveUseCase createInvitationWave;
    private final StartInvitationWaveUseCase startInvitationWave;
    private final PauseInvitationWaveUseCase pauseInvitationWave;
    private final ResumeInvitationWaveUseCase resumeInvitationWave;
    private final CancelInvitationWaveUseCase cancelInvitationWave;
    private final RetryFailedInvitationsUseCase retryFailedInvitations;
    private final GetInvitationWaveUseCase getInvitationWave;
    private final ListInvitationWavesUseCase listInvitationWaves;
    private final ListInvitationWaveRecipientsUseCase listInvitationWaveRecipients;
    private final DownloadInvitationWaveReportUseCase downloadInvitationWaveReport;
    private final GetInvitationWaveSettingsUseCase getInvitationWaveSettings;
    private final ListInvitationImportBatchesUseCase listInvitationImportBatches;
    private final CurrentActorFinder currentActorFinder;
    private final PermissionGate permissionGate;

    public InvitationWaveController(CreateInvitationWaveUseCase createInvitationWave,
                                    StartInvitationWaveUseCase startInvitationWave,
                                    PauseInvitationWaveUseCase pauseInvitationWave,
                                    ResumeInvitationWaveUseCase resumeInvitationWave,
                                    CancelInvitationWaveUseCase cancelInvitationWave,
                                    RetryFailedInvitationsUseCase retryFailedInvitations,
                                    GetInvitationWaveUseCase getInvitationWave,
                                    ListInvitationWavesUseCase listInvitationWaves,
                                    ListInvitationWaveRecipientsUseCase listInvitationWaveRecipients,
                                    DownloadInvitationWaveReportUseCase downloadInvitationWaveReport,
                                    GetInvitationWaveSettingsUseCase getInvitationWaveSettings,
                                    ListInvitationImportBatchesUseCase listInvitationImportBatches,
                                    CurrentActorFinder currentActorFinder, PermissionGate permissionGate) {
        this.createInvitationWave = createInvitationWave;
        this.startInvitationWave = startInvitationWave;
        this.pauseInvitationWave = pauseInvitationWave;
        this.resumeInvitationWave = resumeInvitationWave;
        this.cancelInvitationWave = cancelInvitationWave;
        this.retryFailedInvitations = retryFailedInvitations;
        this.getInvitationWave = getInvitationWave;
        this.listInvitationWaves = listInvitationWaves;
        this.listInvitationWaveRecipients = listInvitationWaveRecipients;
        this.downloadInvitationWaveReport = downloadInvitationWaveReport;
        this.getInvitationWaveSettings = getInvitationWaveSettings;
        this.listInvitationImportBatches = listInvitationImportBatches;
        this.currentActorFinder = currentActorFinder;
        this.permissionGate = permissionGate;
    }

    @RequiresPermission(code = "notification.manage_invitation_waves", legacyRoles = LEGACY_ROLES)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationWaveResponse createInvitationWave(@Valid @RequestBody CreateInvitationWaveCommand command) {
        return createInvitationWave.createInvitationWave(command);
    }

    @RequiresPermission(code = "notification.read_invitation_waves", legacyRoles = LEGACY_ROLES)
    @GetMapping
    public PageResponse<InvitationWaveResponse> listInvitationWaves(@RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "25") int size) {
        return listInvitationWaves.listInvitationWaves(page, size);
    }

    @RequiresPermission(code = "notification.read_invitation_waves", legacyRoles = LEGACY_ROLES)
    @GetMapping("/settings")
    public InvitationWaveSettingsResponse getInvitationWaveSettings() {
        return getInvitationWaveSettings.getInvitationWaveSettings();
    }

    @RequiresPermission(code = "notification.read_invitation_waves", legacyRoles = LEGACY_ROLES)
    @GetMapping("/import-batches")
    public List<InvitationImportBatchEntry> listInvitationImportBatches() {
        return listInvitationImportBatches.listInvitationImportBatches();
    }

    @RequiresPermission(code = "notification.read_invitation_waves", legacyRoles = LEGACY_ROLES)
    @GetMapping("/{id}")
    public InvitationWaveResponse getInvitationWave(@PathVariable Long id) {
        return getInvitationWave.getInvitationWave(id);
    }

    @RequiresPermission(code = "notification.read_invitation_waves", legacyRoles = LEGACY_ROLES)
    @GetMapping("/{id}/recipients")
    public PageResponse<InvitationWaveRecipientEntry> listInvitationWaveRecipients(
            @PathVariable Long id,
            @RequestParam(required = false) InvitationRecipientStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean includePersonalData = permissionGate.allows(authentication, READ_STUDENTS_PERMISSION, READ_STUDENTS_LEGACY_ROLES);
        return listInvitationWaveRecipients.listInvitationWaveRecipients(id, status, page, size, includePersonalData);
    }

    @RequiresPermission(code = "notification.read_invitation_waves", legacyRoles = LEGACY_ROLES)
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/csv", schema = @Schema(type = "string")))
    @GetMapping("/{id}/report.csv")
    public ResponseEntity<byte[]> downloadInvitationWaveReport(@PathVariable Long id) {
        InvitationWaveReportDownload download = downloadInvitationWaveReport.downloadInvitationWaveReport(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(download.content());
    }

    @RequiresPermission(code = "notification.send_invitation_wave", legacyRoles = LEGACY_ROLES)
    @PostMapping("/{id}/start")
    public InvitationWaveResponse startInvitationWave(@PathVariable Long id) {
        return startInvitationWave.startInvitationWave(id, currentActorFinder.currentPersonId());
    }

    @RequiresPermission(code = "notification.manage_invitation_waves", legacyRoles = LEGACY_ROLES)
    @PostMapping("/{id}/pause")
    public InvitationWaveResponse pauseInvitationWave(@PathVariable Long id) {
        return pauseInvitationWave.pauseInvitationWave(id, currentActorFinder.currentPersonId());
    }

    @RequiresPermission(code = "notification.send_invitation_wave", legacyRoles = LEGACY_ROLES)
    @PostMapping("/{id}/resume")
    public InvitationWaveResponse resumeInvitationWave(@PathVariable Long id) {
        return resumeInvitationWave.resumeInvitationWave(id, currentActorFinder.currentPersonId());
    }

    @RequiresPermission(code = "notification.send_invitation_wave", legacyRoles = LEGACY_ROLES)
    @PostMapping("/{id}/cancel")
    public InvitationWaveResponse cancelInvitationWave(@PathVariable Long id) {
        return cancelInvitationWave.cancelInvitationWave(id, currentActorFinder.currentPersonId());
    }

    @RequiresPermission(code = "notification.send_invitation_wave", legacyRoles = LEGACY_ROLES)
    @PostMapping("/{id}/retry-failed")
    public RetryFailedInvitationsResponse retryFailedInvitations(@PathVariable Long id) {
        return new RetryFailedInvitationsResponse(retryFailedInvitations.retryFailedInvitations(id));
    }
}
