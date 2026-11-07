/**
 * <b>Qué es</b>: módulo de plataforma: gobierna los switches de módulos y los ajustes del instituto (modo de facturación, medios de pago, avisos de cobranza) y aprovisiona al primer director.
 *
 * <p><b>Expone</b>: la interfaz con nombre {@code api} ({@code FeatureQuery}, {@code BillingModeGate}, {@code PaymentMethodAvailability}, {@code BillingNotificationsPolicy}) y {@code events}.
 *
 * <p><b>No hacer</b>: acceder a repositorios o entidades de otro módulo; decidir reglas de otro módulo: lo consultan por {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code ModulesController} llama a un caso de uso de {@code usecase/modules}, que lee con {@code EffectiveModulesReader}. Decisión de fondo: ADR 0002 y ADR 0007.
 */
@ApplicationModule(displayName = "Platform", allowedDependencies = {"shared", "identity :: *"})
package com.red_escuela.backend.platform;

import org.springframework.modulith.ApplicationModule;
