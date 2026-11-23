/**
 * <b>Qué es</b>: casos de uso de la oleada de invitaciones: crear con snapshot de destinatarios, iniciar, pausar, reanudar, cancelar y consultar.
 *
 * <p><b>Expone</b>: {@code CreateInvitationWaveUseCase}, {@code StartInvitationWaveUseCase}, {@code PauseInvitationWaveUseCase}, {@code ResumeInvitationWaveUseCase}, {@code CancelInvitationWaveUseCase}, {@code GetInvitationWaveUseCase}, {@code ListInvitationWavesUseCase}, {@code ListInvitationWaveRecipientsUseCase}.
 *
 * <p><b>No hacer</b>: abrir transacciones propias ni enviar correos.
 *
 * <p><b>Ejemplo</b>: {@code CreateInvitationWaveService} pide los candidatos a {@code ImportedAccountsQuery} y delega la persistencia en {@code CreateInvitationWaveTransaction}.
 */
package com.red_escuela.backend.notification.invitation.usecase;
