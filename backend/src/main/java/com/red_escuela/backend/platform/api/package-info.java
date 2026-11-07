/**
 * <b>Qué es</b>: especialistas y puertos que otros módulos consultan: estado de los módulos, modo de facturación y medios de pago habilitados.
 *
 * <p><b>Expone</b>: {@code FeatureQuery}, {@code BillingModeGate}, {@code PaymentMethodAvailability}, {@code BillingNotificationsPolicy}.
 *
 * <p><b>No hacer</b>: exponer entidades o repositorios; agregar un método que otro módulo no use.
 *
 * <p><b>Ejemplo</b>: {@code BillingModeGate} le dice a billing si el modo de facturación está encendido.
 */
@NamedInterface("api")
package com.red_escuela.backend.platform.api;

import org.springframework.modulith.NamedInterface;
