---
title: Activar módulos de la plataforma
section: institution
roles: [platform_admin]
status: borrador
screens: ["modulos"]
last_review: 2026-11-28
---

## Para qué sirve

Para prender o apagar los módulos opcionales del instituto, con el motivo anotado en un historial. Por ejemplo, las estadísticas o los medios de pago.

## Quién puede

Solo el administrador de la plataforma. Se entra desde «Módulos».

## Pasos

1. Entrá a «Módulos». Los módulos están agrupados en «Cobros y pagos» y «Reportes y estadísticas». Cada grupo indica cuántos están activos.
2. Cada módulo muestra su nombre, una descripción y su estado: «Habilitado», «Deshabilitado» o «No disponible».
3. Para cambiarlo, usá el interruptor del módulo.
4. Se abre una ventana con la tabla «Hoy / Nuevo / Diferencia». Escribí el «Motivo» (entre 5 y 500 caracteres) y tocá «Confirmar».
5. Para ver cambios anteriores, bajá hasta el historial de cambios. Podés filtrar por módulo, por quién lo hizo («Personas» o «Sistema») y por fechas.

## Reglas

- El motivo es obligatorio y queda en el historial.
- Varios módulos de cobros dependen de Facturación: si Facturación no está en modo «Completa», figuran como «No disponible · Necesita Facturación completa» y el interruptor está bloqueado.
- «Facturación externa» figura como «Bloqueado: importación sin definir» y no se puede cambiar.
- Habilitar un medio de pago lo habilita para el instituto. El Director decide si lo prende.
- Si otra persona cambió el mismo módulo mientras lo editabas, la ventana avisa quién y cuándo, y ofrece «Recargar y reintentar». Tu motivo se conserva.
- Si el módulo ya estaba en el estado pedido, la ventana dice «Ya estaba así» y se cierra con «Cerrar».
- El modo de la Facturación (apagada, calcula sin cobrar, completa) pertenece a la sección Cobranza, que está pendiente de rediseño. Esta guía no cubre ese cambio.

## Qué pasa después

- El módulo queda habilitado o deshabilitado para el instituto.
- El cambio queda en el historial, con quién lo hizo y el motivo.

## Errores comunes

- «Este módulo todavía no está configurado en el instituto»: el módulo no está cargado.
- «El motivo debe tener entre 5 y 500 caracteres».
- «Lo cambió [persona] el [fecha]»: alguien se adelantó. Recargá y volvé a intentar.
- «Todavía no hay módulos configurados»: el instituto no tiene módulos cargados.

**Pendiente de verificar:**

- El efecto concreto de prender o apagar cada módulo en las pantallas de los demás roles no se probó en pantalla.
