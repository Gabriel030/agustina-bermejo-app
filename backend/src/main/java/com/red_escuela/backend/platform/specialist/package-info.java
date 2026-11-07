/**
 * <b>Qué es</b>: especialistas internos: cambian switches y ajustes, leen el estado efectivo, firman confirmaciones, aprovisionan al primer director y calculan la huella de cada pedido. Incluye el runner del modo de facturación inicial.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso y la {@code api}. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code PlatformFeatureChanger}.
 */
package com.red_escuela.backend.platform.specialist;
