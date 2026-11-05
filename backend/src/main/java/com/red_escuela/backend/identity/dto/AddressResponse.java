package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.shared.domain.Address;

public record AddressResponse(
        @Schema(nullable = true) String street,
        @Schema(nullable = true) String number,
        @Schema(nullable = true) String floor,
        @Schema(nullable = true) String city,
        @Schema(nullable = true) String state,
        @Schema(nullable = true) String postalCode,
        @Schema(nullable = true) String country) {

    public static AddressResponse from(Address a) {
        if (a == null) return null;
        return new AddressResponse(
                a.getStreet(), a.getNumber(), a.getFloor(),
                a.getCity(), a.getState(), a.getPostalCode(), a.getCountry());
    }
}
