package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.entity.Address;

public record AddressResponse(
        String calle,
        String numero,
        String piso,
        String ciudad,
        String provincia,
        String codigoPostal,
        String pais) {

    public static AddressResponse from(Address a) {
        if (a == null) return null;
        return new AddressResponse(
                a.getStreet(), a.getNumber(), a.getFloor(),
                a.getCity(), a.getState(), a.getPostalCode(), a.getCountry());
    }
}
