# ADR 0003 — Eventos entre módulos con Transactional Outbox, sin broker externo

> Verificado: 2026-10-31, commit `cd1ede80`.

- **Estado:** aceptado (decisión tomada antes de este ADR; se implementó en la Fase M1 de la Épica 18; la fecha exacta de la decisión no está registrada en las fuentes)
- **Referencia:** ADR 0002 (los eventos son una de las tres superficies de un módulo).

## Por qué

Los módulos inferiores no pueden llamar hacia arriba y los efectos externos (mails, notificaciones) no deben perderse si el proceso cae después del commit. Se decidió no introducir un broker externo (Kafka, RabbitMQ): hay una sola aplicación por instituto y una sola base, y un broker sumaría infraestructura que operar en cada despliegue.

## Decisión

- Los eventos de dominio se publican con el **Event Publication Registry** de Spring Modulith (`spring-modulith-starter-jdbc`). El evento queda guardado en la tabla `event_publication` (migración V90) en la misma transacción que el cambio de negocio y se entrega después del commit, con reintento.
- Los consumidores usan `@ApplicationModuleListener` (asíncrono, transaccional, después del commit). Hoy lo usan los listeners del módulo `notification` (billing, comunicados, asistencia, avisos de cobranza, notas de crédito) y el envío de mails de `identity`.
- Configuración en `backend/src/main/resources/application.properties`: `spring.modulith.events.republish-outstanding-events-on-restart=true` (los eventos pendientes se reenvían al arrancar) y `spring.modulith.events.completion-mode=delete` (los completados se borran).
- Los eventos llevan ids y valores planos, nunca entidades (regla de ArchUnit `domainEventsCarryNoEntities`).
- Un `TaskDecorator` copia el MDC del hilo del request al hilo del listener (patrón Decorator).

## Consecuencias

- Entrega al menos una vez: los listeners deben ser idempotentes.
- Sin broker: no hay consumo desde otros procesos; si algún día hiciera falta, el registro de eventos es el punto donde se conectaría un publicador externo.

## Falta confirmar

- Fecha de la decisión original y quién la tomó (solo consta como "decisión ya tomada" en la Fase M1 del vault).
- Política de caché: la nota de memoria del proyecto dice "caché solo si hay medición"; no hay fuente en el vault, por eso no se incluye como decisión acá.
