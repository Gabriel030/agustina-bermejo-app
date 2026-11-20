package com.red_escuela.backend.shared.permission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
class GateProbeService {

    @PreAuthorize("@permissionGate.allows(authentication, 'probe.usecase', 'ROLE_DIRECTOR', 'ROLE_ADMINISTRATIVO')")
    String execute() {
        return "usecase-ok";
    }
}
