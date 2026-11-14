package com.red_escuela.backend.shared.audit.service;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.audit.AuditRecord;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class AuditTrail {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditTrail(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditRecord record) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        auditLogRepository.save(AuditLogEntry.builder()
                .occurredAt(LocalDateTime.now(clock))
                .actorPersonId(authenticated ? actorId(authentication) : null)
                .actorRole(authenticated ? actorRole(authentication) : null)
                .action(record.action())
                .entityType(record.entityType())
                .entityId(record.entityId())
                .beforeValue(record.beforeValue())
                .afterValue(record.afterValue())
                .reason(record.reason())
                .build());
    }

    private static Long actorId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String actorRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .findFirst()
                .orElse(null);
    }
}
