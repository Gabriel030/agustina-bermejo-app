---
title: Aprobar la oferta del ciclo
section: set-up-school-year
roles: [director]
status: borrador
screens: ["aprobaciones/oferta-academica", "aprobaciones/oferta-academica/:id"]
last_review: 2026-11-28
---

## Para qué sirve

Cuando Preceptoría termina de armar un ciclo, lo envía a aprobación. El Director lo revisa y lo aprueba o lo rechaza. Al aprobarlo se activan sus comisiones y quedan disponibles para la cursada.

## Quién puede

Solo el Director. Entra desde «Aprobaciones», que está en el menú, a «Oferta por aprobar».

## Pasos

1. Entrá a «Aprobaciones» y abrí la tarjeta «Oferta por aprobar». La lista «Oferta por aprobar» muestra los ciclos enviados por Preceptoría, con carrera o curso, período, cantidad de comisiones, quién lo envió y cuándo.
2. Tocá un ciclo para ver el detalle. Arriba figuran la carrera o el curso, el período y quién lo envió.
3. Si hay cruces de aulas o de docentes, aparece el cartel «Hay N cruces para revisar antes de aprobar», con el detalle de cada uno. La comisión afectada lleva la marca «Cruce».
4. Revisá la tabla «Comisiones del ciclo»: materia, comisión, turno, docente y cupo.
5. Para aprobar, tocá «Aprobar ciclo». La barra dice cuántas comisiones se activan. Confirmá con «Confirmar aprobación». Aparece «Ciclo aprobado».
6. Para rechazar, tocá «Rechazar», escribí qué hay que corregir y tocá «Rechazar ciclo». Aparece «Ciclo rechazado».

## Reglas

- Solo se decide un ciclo «Pendiente». Uno ya decidido se muestra como «Este ciclo ya no está pendiente», con quién lo decidió y cuándo.
- El rechazo exige un motivo de al menos 10 caracteres; la pantalla muestra el contador. Preceptoría recibe ese motivo para corregir y volver a enviar.
- Al aprobar, se activan todas las comisiones del ciclo.
- No se puede aprobar un ciclo cuyo período está inactivo o ya terminó.
- No se puede aprobar si hay cruces de aula o de docente que no se resolvieron.
- En un ciclo de curso, todas las comisiones tienen que tener preceptor antes de aprobar.
- Si otra persona de Dirección ya decidió el mismo ciclo, la pantalla avisa «Otra persona de dirección ya decidió este trámite».

## Qué pasa después

Las comisiones pasan a «Activa» y el ciclo a «Aprobado». La decisión le llega a Preceptoría. Para abrir una cohorte hace falta que las comisiones de primer año estén aprobadas. Ver «Abrir una cohorte». Si se rechazó, el Preceptor lo retoma con «Corregir y reenviar».

## Errores comunes

- «Otra persona de dirección ya decidió este trámite»: otro director se adelantó. No hay que hacer nada más.
- «No pudimos comprobar los cruces de aulas y docentes»: tocá «Reintentar» antes de decidir.
- Aprobar y que la cohorte siga sin poder abrirse: faltan preceptores en las comisiones. Se asignan en la ficha de la carrera, dentro de «Oferta académica». Ver «Crear comisiones y asignar preceptores».
- «No se pudo guardar la decisión. Probá de nuevo.»: es el mensaje genérico de cualquier falla al decidir.

**Pendiente de verificar:** el mensaje que ve el Director cuando el sistema no deja aprobar por falta de preceptor en un curso o por un cruce. La pantalla puede mostrar solo el mensaje genérico. También se debe confirmar con una prueba real si un cruce bloquea la aprobación o solo avisa. En el código de aprobación el sistema revisa los cruces y no aprueba si los encuentra.
