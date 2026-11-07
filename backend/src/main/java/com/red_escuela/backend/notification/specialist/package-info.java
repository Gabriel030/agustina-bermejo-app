/**
 * <b>Qué es</b>: especialistas internos: listeners de eventos de otros módulos, compositores de mensajes, canales y despacho de notificaciones in-app, y bandeja del usuario.
 *
 * <p><b>Expone</b>: nada. Se activan por eventos o los llama el controller. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase}; renombrar un listener sin revisar la clave persistida de idempotencia ({@code *_HANDLER}).
 *
 * <p><b>Ejemplo</b>: {@code BillingEventsNotificationListener}.
 */
package com.red_escuela.backend.notification.specialist;
