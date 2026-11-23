/**
 * <b>Qué es</b>: estados, motivos de exclusión, eventos, parámetros validados y máquina de estados de la oleada de invitaciones.
 *
 * <p><b>Expone</b>: {@code InvitationWaveStatus}, {@code InvitationWaveStatusStateMachine}, {@code InvitationWaveParameters}.
 *
 * <p><b>No hacer</b>: depender de infraestructura ni de Spring.
 *
 * <p><b>Ejemplo</b>: {@code InvitationWaveStatusStateMachine.requireTargetStateFor(DRAFT, START)} devuelve {@code RUNNING}.
 */
package com.red_escuela.backend.notification.invitation.domain;
