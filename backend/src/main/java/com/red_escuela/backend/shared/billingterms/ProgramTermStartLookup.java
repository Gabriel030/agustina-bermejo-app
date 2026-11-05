package com.red_escuela.backend.shared.billingterms;

import java.time.LocalDate;
import java.util.Optional;

public interface ProgramTermStartLookup {
    Optional<LocalDate> findTermStartOfTemplate(Long templateId);
}
