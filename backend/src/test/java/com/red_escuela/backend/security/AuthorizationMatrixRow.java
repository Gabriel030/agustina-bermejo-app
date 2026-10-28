package com.red_escuela.backend.security;

import java.util.Map;

record AuthorizationMatrixRow(
        String method,
        String path,
        AccessExpectation anonymous,
        Map<MatrixRole, AccessExpectation> byRole,
        String ownership,
        String body) {

    String key() {
        return method + " " + path;
    }
}
