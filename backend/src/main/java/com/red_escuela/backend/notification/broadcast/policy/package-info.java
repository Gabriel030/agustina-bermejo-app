/**
 * <b>Qué es</b>: reglas puras que deciden si una comunicación masiva puede salir por un canal según el consentimiento.
 *
 * <p><b>Expone</b>: {@code XxxPolicy} y su contexto.
 *
 * <p><b>No hacer</b>: usar repositorios o {@code Clock}; depender de {@code usecase}.
 *
 * <p><b>Ejemplo</b>: {@code BroadcastConsentPolicy}.
 */
package com.red_escuela.backend.notification.broadcast.policy;
