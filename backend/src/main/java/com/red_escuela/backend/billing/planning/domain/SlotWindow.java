package com.red_escuela.backend.billing.planning.domain;

import java.time.LocalDate;

public record SlotWindow(LocalDate opensOn, LocalDate closesOn) {
    public static String key(Long templateId, int slot) {
        return templateId + ":" + slot;
    }
}
