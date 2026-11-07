package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FirstDirectorProvisioningStatus;
import com.red_escuela.backend.platform.specialist.FirstDirectorStatusReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class FindFirstDirectorStatusService implements FindFirstDirectorStatusUseCase {

    private final FirstDirectorStatusReader firstDirectorStatusReader;

    public FindFirstDirectorStatusService(FirstDirectorStatusReader firstDirectorStatusReader) {
        this.firstDirectorStatusReader = firstDirectorStatusReader;
    }

    @Override
    public FirstDirectorProvisioningStatus findStatus() {
        return firstDirectorStatusReader.readStatus();
    }
}
