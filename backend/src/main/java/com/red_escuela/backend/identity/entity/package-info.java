/**
 * <b>Qué es</b>: entidades JPA de identidad: personas, alumnos, instituciones, tokens, intentos de inicio de sesión, preferencias y cambios de estado.
 *
 * <p><b>Expone</b>: nada hacia afuera salvo {@code Person} y {@code Student}, que otros módulos referencian por relación.
 *
 * <p><b>No hacer</b>: poner lógica que consulte otros agregados; exponerlas en {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code Person}.
 */
package com.red_escuela.backend.identity.entity;
