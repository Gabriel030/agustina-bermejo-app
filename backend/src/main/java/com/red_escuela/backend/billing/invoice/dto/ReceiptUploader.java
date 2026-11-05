package com.red_escuela.backend.billing.invoice.dto;

import com.red_escuela.backend.identity.entity.Person;

import java.util.List;

public record ReceiptUploader(Long personId, String name, String role, boolean internal) {

    private static final List<String> ROLE_PRIORITY = List.of("DIRECTOR", "ADMINISTRATIVO", "PRECEPTOR", "DOCENTE", "ALUMNO");

    public static ReceiptUploader of(Person uploadedBy, Person payer, List<String> roleNames) {
        if (uploadedBy == null) {
            return null;
        }
        boolean internal = payer == null || !uploadedBy.getId().equals(payer.getId());
        String role = ROLE_PRIORITY.stream().filter(roleNames::contains).findFirst().orElse(null);
        return new ReceiptUploader(uploadedBy.getId(), uploadedBy.getName() + " " + uploadedBy.getLastname(), role, internal);
    }
}
