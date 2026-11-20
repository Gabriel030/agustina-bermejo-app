package com.red_escuela.backend.shared.permission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GateProbeController {

    private final GateProbeService service;

    GateProbeController(GateProbeService service) {
        this.service = service;
    }

    @GetMapping("/probe/controller")
    @PreAuthorize("@permissionGate.allows(authentication, 'probe.read', 'ROLE_DOCENTE', 'ROLE_DIRECTOR')")
    String controllerGate() {
        return "controller-ok";
    }

    @GetMapping("/probe/single")
    @PreAuthorize("@permissionGate.allows(authentication, 'probe.single', 'ROLE_DIRECTOR')")
    String singleRole() {
        return "single-ok";
    }

    @GetMapping("/probe/usecase")
    String usecaseGate() {
        return service.execute();
    }

    @GetMapping("/probe/meta")
    @RequiresProbePermission(permission = "probe.meta", legacyRoles = "ROLE_DOCENTE,ROLE_DIRECTOR")
    String metaGate() {
        return "meta-ok";
    }

    @GetMapping("/probe/legacy")
    @PreAuthorize("hasAnyRole('DOCENTE', 'DIRECTOR')")
    String legacyRule() {
        return "legacy-ok";
    }
}
