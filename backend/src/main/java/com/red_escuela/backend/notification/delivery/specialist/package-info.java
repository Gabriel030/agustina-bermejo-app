/**
 * <b>Qué es</b>: especialistas de entrega: encolan, planifican y envían emails y entregas in-app, y los jobs de despacho.
 *
 * <p><b>Expone</b>: las interfaces de origen de despacho ({@code EmailDispatchSource}) y los canales.
 *
 * <p><b>No hacer</b>: depender de {@code usecase}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code EmailDeliveryDispatchJob}.
 */
package com.red_escuela.backend.notification.delivery.specialist;
