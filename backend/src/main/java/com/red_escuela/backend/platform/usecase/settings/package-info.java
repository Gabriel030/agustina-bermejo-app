/**
 * <b>Qué es</b>: casos de uso de los ajustes del instituto: leer, cambiar el aviso de cobranza y el medio de pago, listar cambios.
 *
 * <p><b>Expone</b>: interfaces {@code XxxUseCase} con su implementación {@code XxxService}.
 *
 * <p><b>No hacer</b>: llamar a otro caso de uso; acceder a repositorios directo.
 *
 * <p><b>Ejemplo</b>: {@code ChangePaymentMethodSettingService} usa {@code InstituteSettingChanger}.
 */
package com.red_escuela.backend.platform.usecase.settings;
