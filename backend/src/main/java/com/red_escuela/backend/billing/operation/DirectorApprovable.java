package com.red_escuela.backend.billing.operation;

import java.time.LocalDate;

public interface DirectorApprovable {
    boolean requestedByDirector();

    String authorizationReason();

    LocalDate authorizationExpiresOn();
}
