package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.FirstDirectorProvisioning;

public record FirstDirectorProvisioningResponse(Long institutionId, boolean institutionCreated, Long directorPersonId, String directorEmail) {

    public static FirstDirectorProvisioningResponse of(FirstDirectorProvisioning provisioning) {
        return new FirstDirectorProvisioningResponse(
                provisioning.institutionId(), provisioning.institutionCreated(), provisioning.directorPersonId(), provisioning.directorEmail());
    }
}
