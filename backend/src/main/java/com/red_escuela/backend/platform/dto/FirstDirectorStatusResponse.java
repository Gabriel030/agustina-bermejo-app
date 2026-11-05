package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.FirstDirectorProvisioningStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record FirstDirectorStatusResponse(boolean directorExists, @Schema(nullable = true) String institutionName) {

    public static FirstDirectorStatusResponse of(FirstDirectorProvisioningStatus status) {
        return new FirstDirectorStatusResponse(status.directorExists(), status.institutionName());
    }
}
