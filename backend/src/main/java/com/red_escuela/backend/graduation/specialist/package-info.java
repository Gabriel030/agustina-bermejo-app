/**
 * <b>Qué es</b>: especialistas internos: registran, aprueban y rechazan solicitudes de graduación, verifican elegibilidad y deuda, y emiten los certificados de programa. Incluye el adaptador de {@code GraduationRequestTriggerPort}.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso y los jobs de este módulo. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code GraduationRequestRegistrar}.
 */
package com.red_escuela.backend.graduation.specialist;
