package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FirstDirectorProvisioning;
import com.red_escuela.backend.platform.domain.FirstDirectorProvisioningCommand;
import com.red_escuela.backend.platform.specialist.FirstDirectorProvisioner;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ProvisionFirstDirectorService implements ProvisionFirstDirectorUseCase {

    private final FirstDirectorProvisioner firstDirectorProvisioner;

    public ProvisionFirstDirectorService(FirstDirectorProvisioner firstDirectorProvisioner) {
        this.firstDirectorProvisioner = firstDirectorProvisioner;
    }

    @Override
    public FirstDirectorProvisioning provision(FirstDirectorProvisioningCommand command) {
        return firstDirectorProvisioner.provisionFirstDirector(command);
    }
}
