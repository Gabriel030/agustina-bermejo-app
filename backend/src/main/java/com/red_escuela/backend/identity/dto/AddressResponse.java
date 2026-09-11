package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.entity.Address;

public record AddressResponse(
        String street,
        String number,
        String floor,
        String city,
        String state,
        String postalCode,
        String country) {

    public static AddressResponse from(Address a) {
        if (a == null) return null;
        return new AddressResponse(
                a.getStreet(), a.getNumber(), a.getFloor(),
                a.getCity(), a.getState(), a.getPostalCode(), a.getCountry());
    }
}
