package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@TestConfiguration
@Import(GatePermissionProbeConfiguration.GridProbeController.class)
public class GatePermissionProbeConfiguration {

    @RestController
    @Profile("gate-probe")
    public static class GridProbeController {

        @GetMapping("/grid-probe/create")
        @RequiresPermission(code = "announcement.create", legacyRoles = "ROLE_ADMINISTRATIVO")
        String create() {
            return "ok";
        }

        @GetMapping("/grid-probe/docente-legacy")
        @RequiresPermission(code = "announcement.create", legacyRoles = "ROLE_DOCENTE")
        String docenteLegacy() {
            return "ok";
        }

        @GetMapping("/grid-probe/fixed")
        @RequiresPermission(code = "announcement.fixed_probe", legacyRoles = "ROLE_DIRECTOR,ROLE_DOCENTE")
        String fixed() {
            return "ok";
        }
    }
}
