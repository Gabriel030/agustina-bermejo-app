package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.identity.api.FirstDirectorStatus;
import com.red_escuela.backend.identity.api.FirstDirectorStatusFinder;
import com.red_escuela.backend.platform.domain.FirstDirectorProvisioningStatus;
import org.springframework.stereotype.Service;

@Service
public class FirstDirectorStatusReader {

    private final FirstDirectorStatusFinder firstDirectorStatusFinder;

    public FirstDirectorStatusReader(FirstDirectorStatusFinder firstDirectorStatusFinder) {
        this.firstDirectorStatusFinder = firstDirectorStatusFinder;
    }

    public FirstDirectorProvisioningStatus readStatus() {
        FirstDirectorStatus status = firstDirectorStatusFinder.findFirstDirectorStatus();
        return new FirstDirectorProvisioningStatus(status.directorExists(), status.institutionName());
    }
}
