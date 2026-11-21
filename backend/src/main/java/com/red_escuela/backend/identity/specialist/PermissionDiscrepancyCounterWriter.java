package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Permission;
import com.red_escuela.backend.identity.repository.PermissionDiscrepancyCounterRepository;
import com.red_escuela.backend.identity.repository.PermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PermissionDiscrepancyCounterWriter {

    private static final Logger log = LoggerFactory.getLogger(PermissionDiscrepancyCounterWriter.class);

    private final PermissionRepository permissionRepository;
    private final PermissionDiscrepancyCounterRepository counterRepository;

    public PermissionDiscrepancyCounterWriter(PermissionRepository permissionRepository,
                                              PermissionDiscrepancyCounterRepository counterRepository) {
        this.permissionRepository = permissionRepository;
        this.counterRepository = counterRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void add(Map<PermissionDiscrepancyKey, Long> occurrencesByKey, Instant seenAt) {
        Map<String, Permission> permissionByCode = permissionRepository.findAllByCodeIn(
                        occurrencesByKey.keySet().stream().map(PermissionDiscrepancyKey::permission).distinct().toList())
                .stream().collect(Collectors.toMap(Permission::getCode, Function.identity()));
        occurrencesByKey.forEach((key, occurrences) -> {
            Permission permission = permissionByCode.get(key.permission());
            if (permission != null) {
                counterRepository.addOccurrences(key.role(), permission.getModule(), permission.getId(), occurrences, seenAt);
            } else {
                log.warn("Discrepancia de un permiso que no está en el catálogo: {}", key.permission());
            }
        });
    }
}
