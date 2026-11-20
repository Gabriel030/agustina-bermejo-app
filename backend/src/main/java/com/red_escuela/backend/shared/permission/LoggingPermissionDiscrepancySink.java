package com.red_escuela.backend.shared.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingPermissionDiscrepancySink implements PermissionDiscrepancySink {

    private static final Logger log = LoggerFactory.getLogger(LoggingPermissionDiscrepancySink.class);

    @Override
    public void record(String role, String permission, boolean legacyDecision, boolean permissionDecision) {
        log.warn("permission shadow discrepancy role={} permission={} legacy={} permissionTable={}",
                role, permission, legacyDecision, permissionDecision);
    }
}
