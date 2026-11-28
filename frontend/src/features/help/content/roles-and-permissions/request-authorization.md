---
title: Pedir y resolver una autorización
section: roles-and-permissions
roles: [administrativo, subdirector, director]
status: borrador
screens: ["aprobaciones"]
last_review: 2026-11-28
---

## Para qué sirve

Algunas operaciones de facturación no se ejecutan en el momento: necesitan que las autorice el Director. La pantalla «Aprobaciones» sirve para seguir lo que pediste y, si sos Director, para decidirlo.

## Quién puede

- Administrativo y Subdirector: piden la autorización, siguen el estado de sus pedidos y pueden retirarlos.
- Director: aprueba o rechaza los pedidos de otras personas.

## Pasos

**Pedir una autorización (ejemplo: un reembolso)**

1. Entrá a la ficha del alumno y buscá la sección «Reembolsos».
2. Tocá «Pedir reembolso».
3. Completá los datos que pide el formulario.
4. Tocá «Pedir reembolso» y confirmá en «¿Pedir el reembolso de…?».
5. Aparece «Pedido enviado a la dirección.» Con «Ver mis pedidos» vas directo a la bandeja.

**Seguir tus pedidos (Administrativo y Subdirector)**

1. Entrá a «Aprobaciones». La solapa «Mis pedidos» muestra los pendientes.
2. Arriba ves «Vencen en 7 días» y «A devolver».
3. Tocá un pedido para ver el detalle: «Qué se pide», quién lo pidió y su historia.
4. En «Historial» ves los pedidos ya decididos o retirados. Podés buscar por alumno.
5. Para retirar un pedido pendiente, tocá «Retirar pedido» y confirmá en «Retirar el pedido».

**Resolver un pedido (Director)**

1. Entrá a «Aprobaciones». Ves «Trámites esperando tu decisión» con contadores por tipo.
2. Tocá «Operaciones de facturación». Se abre la bandeja «Pendientes».
3. Abrí un pedido y leé «Qué se pide» y «Si aprobás».
4. Si querés, escribí una «Nota (opcional)».
5. Para aprobar, tocá «Aprobar» (con el monto, si lo tiene) y después «Confirmar aprobación».
6. Para rechazar, tocá «Rechazar», escribí el motivo en «Rechazar pedido» y confirmá.

## Reglas

- Solo el Director aprueba o rechaza.
- El Director no puede decidir un pedido que hizo él mismo: lo tiene que decidir otra persona de dirección.
- Al aprobar, la operación se ejecuta ahora y no se puede deshacer.
- El motivo de un rechazo es obligatorio. Lo lee quien hizo el pedido.
- Solo quien hizo el pedido puede retirarlo, y solo mientras está pendiente.
- Si nadie decide a tiempo, el pedido vence y no se ejecuta.
- Un reembolso pedido por el Director se aprueba en el momento y no necesita otra aprobación. Si lo pide un Administrativo o un Subdirector, queda pendiente del Director y la plata se reserva desde el pedido.
- Aprobar, rechazar y retirar requieren que el módulo de facturación esté activo en modo completo.
- Los estados posibles son: Pendiente, Aprobada, Rechazada, Retirada, Expirada y No se pudo ejecutar.
- La pantalla «Aprobaciones» del Director junta también otros trámites: egresos, excepciones, configuración de evaluación, oferta académica y cambios de horario. Cada uno se explica en su tarea.

> Pendiente de verificar: la lista completa de operaciones que piden autorización y desde qué pantalla se pide cada una. Solo se confirmó el reembolso. La cobranza está pendiente de rediseño.

## Qué pasa después

- Aprobada: la operación se ejecuta. El pedido muestra «Se ejecutó sin problemas.»
- Rechazada: no se hace ningún cambio en la cuenta. Quien pidió ve el motivo.
- Retirada: la dirección ya no la ve para decidir y no se ejecuta.
- Expirada: no se ejecuta.
- «No se pudo ejecutar»: se aprobó pero no se aplicó. No se hizo ningún cambio en la cuenta. Hay que volver a cargar el pedido desde la ficha del alumno.

## Errores comunes

- «No podés decidir un pedido que hiciste vos. Tiene que decidirlo otra persona de dirección.»
- «Solo la dirección puede aprobar pedidos.» o «…rechazar pedidos.»
- «Solo quien hizo el pedido puede retirarlo.»
- «Este pedido ya no está pendiente: alguien lo resolvió antes. Actualizamos los datos.»
- «No encontramos este pedido. Puede que ya no exista.»
- «No tenés acceso a este pedido.»
- «No pudimos cargar los pedidos.» Reintentá en un momento.
