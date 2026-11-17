package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.shared.domain.DocumentType;

public record DocumentKey(DocumentType type, String number) {
}
