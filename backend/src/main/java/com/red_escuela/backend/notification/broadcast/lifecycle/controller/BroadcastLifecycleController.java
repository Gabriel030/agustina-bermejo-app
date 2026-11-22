package com.red_escuela.backend.notification.broadcast.lifecycle.controller;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastDetailResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastRecipientResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastStatusResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastSummaryResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.CancelBroadcastRequest;
import com.red_escuela.backend.notification.broadcast.lifecycle.usecase.CancelBroadcastUseCase;
import com.red_escuela.backend.notification.broadcast.lifecycle.usecase.ConfirmBroadcastUseCase;
import com.red_escuela.backend.notification.broadcast.lifecycle.usecase.GetBroadcastUseCase;
import com.red_escuela.backend.notification.broadcast.lifecycle.usecase.ListBroadcastRecipientsUseCase;
import com.red_escuela.backend.notification.broadcast.lifecycle.usecase.ListBroadcastsUseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/broadcasts")
public class BroadcastLifecycleController {

    private final ConfirmBroadcastUseCase confirmBroadcast;
    private final CancelBroadcastUseCase cancelBroadcast;
    private final ListBroadcastsUseCase listBroadcasts;
    private final GetBroadcastUseCase getBroadcast;
    private final ListBroadcastRecipientsUseCase listBroadcastRecipients;

    public BroadcastLifecycleController(ConfirmBroadcastUseCase confirmBroadcast, CancelBroadcastUseCase cancelBroadcast,
                                        ListBroadcastsUseCase listBroadcasts, GetBroadcastUseCase getBroadcast,
                                        ListBroadcastRecipientsUseCase listBroadcastRecipients) {
        this.confirmBroadcast = confirmBroadcast;
        this.cancelBroadcast = cancelBroadcast;
        this.listBroadcasts = listBroadcasts;
        this.getBroadcast = getBroadcast;
        this.listBroadcastRecipients = listBroadcastRecipients;
    }

    @RequiresPermission(code = "notification.confirm_broadcast", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @PostMapping("/{id}/confirm")
    public BroadcastStatusResponse confirmBroadcast(@PathVariable Long id) {
        return confirmBroadcast.confirmBroadcast(id);
    }

    @RequiresPermission(code = "notification.cancel_broadcast", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @PostMapping("/{id}/cancel")
    public BroadcastStatusResponse cancelBroadcast(@PathVariable Long id, @Valid @RequestBody CancelBroadcastRequest request) {
        return cancelBroadcast.cancelBroadcast(id, request.reason());
    }

    @RequiresPermission(code = "notification.read_broadcasts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @GetMapping
    public PageResponse<BroadcastSummaryResponse> listBroadcasts(@RequestParam(required = false) BroadcastStatus status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return listBroadcasts.listBroadcasts(status, page, size);
    }

    @RequiresPermission(code = "notification.read_broadcasts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @GetMapping("/{id}")
    public BroadcastDetailResponse getBroadcast(@PathVariable Long id) {
        return getBroadcast.getBroadcast(id);
    }

    @RequiresPermission(code = "notification.read_broadcasts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @GetMapping("/{id}/recipients")
    public PageResponse<BroadcastRecipientResponse> listBroadcastRecipients(@PathVariable Long id,
                                                                            @RequestParam(required = false) BroadcastRecipientStatus status,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "50") int size) {
        return listBroadcastRecipients.listBroadcastRecipients(id, status, page, size);
    }
}
