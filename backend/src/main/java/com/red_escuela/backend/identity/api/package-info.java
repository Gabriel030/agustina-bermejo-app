/**
 * <b>Qué es</b>: contrato de identidad hacia otros módulos: buscadores, creadores y verificadores de personas, alumnos y personal, vistas, y los puertos que implementan otros módulos.
 *
 * <p><b>Expone</b>: todo el paquete, como interfaz con nombre {@code api}.
 *
 * <p><b>No hacer</b>: exponer entidades o repositorios; agregar un método que otro módulo pueda resolver con un evento.
 *
 * <p><b>Ejemplo</b>: {@code PersonNameFinder}.
 */
@NamedInterface("api")
package com.red_escuela.backend.identity.api;

import org.springframework.modulith.NamedInterface;
