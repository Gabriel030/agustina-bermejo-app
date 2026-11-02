package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FirstDirectorProvisioning;
import com.red_escuela.backend.platform.domain.FirstDirectorProvisioningCommand;

public interface ProvisionFirstDirectorUseCase {
    FirstDirectorProvisioning provision(FirstDirectorProvisioningCommand command);
}
