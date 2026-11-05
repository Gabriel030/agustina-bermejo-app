package com.red_escuela.backend.identity.api;

import java.util.List;

public record AuthenticatedPrincipal(String personId, List<String> roles) {
}
