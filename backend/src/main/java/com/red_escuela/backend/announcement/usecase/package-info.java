/**
 * <b>Qué es</b>: el guion de cada flujo de avisos. Cada caso de uso es un par {@code XxxUseCase} y {@code XxxService} (su implementación, con {@code @UseCase}), agrupado por subdominio.
 *
 * <p><b>Expone</b>: nada. Es interno del módulo.
 *
 * <p><b>No hacer</b>: llamar a otro caso de uso; abrir {@code @Transactional}; tocar repositorios.
 *
 * <p><b>Ejemplo</b>: los subpaquetes {@code broadcast} (alta, edición, listados y lecturas) y {@code audience} (segmentos y vista previa).
 */
package com.red_escuela.backend.announcement.usecase;
