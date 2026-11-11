package com.red_escuela.backend.enrollment.api;

import java.util.Set;

public record StudentDocumentsStanding(boolean complete, Set<String> missingDocuments) {
}
