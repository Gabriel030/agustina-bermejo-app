---
title: Cerrar una comisión
section: monthly-and-yearly
roles: [docente, preceptor, administrativo, subdirector, director]
status: borrador
screens: ["mis-comisiones/:sectionId/cierre", "cierre-de-comision/:sectionId", "cierres-de-comision"]
last_review: 2026-11-28
---

## Para qué sirve

Al terminar la cursada, cada comisión tiene una planilla de cierre con las notas, la asistencia y el resultado sugerido de cada alumno. El docente la confirma, Administración la aprueba y se emite el acta con libro y folio.

## Quién puede

- **Docente:** revisa y confirma la planilla de sus comisiones.
- **Administrativo y Subdirector:** aprueban el cierre y emiten el acta desde **Cierres de comisión**.
- **Director:** ve la lista y las planillas, y puede reabrir un cierre ya cerrado.
- **Preceptor:** consulta la planilla de la comisión, sin aprobar ni reabrir.

## Pasos

Docente:

1. Entrá a **Mis comisiones** y abrí la comisión.
2. Tocá **Cierre**.
3. Revisá la planilla. Al tocar una tarjeta de resumen, la lista se filtra por ese resultado.
4. Si falta publicar notas, el aviso dice cuáles. Publicalas antes de seguir.
5. Tocá **Confirmar cierre** y confirmá en el cuadro.

Administrativo o Subdirector:

1. Entrá a **Cierres de comisión**. Las comisiones confirmadas por el docente están en el filtro «Para aprobar».
2. Si hace falta, buscá por materia o comisión, o filtrá por término.
3. Abrí la comisión y revisá la planilla. **Exportar** baja la planilla en .xlsx; el PDF del acta se habilita al cerrar.
4. Tocá **Aprobar y cerrar**.
5. Paso 1 de 2: cargá el **Libro** y el **Folio** donde se asienta el acta y tocá **Continuar**.
6. Paso 2 de 2: revisá los totales y tocá **Aprobar y cerrar**.

Director, para reabrir un cierre ya cerrado:

1. Abrí la planilla desde **Cierres de comisión**.
2. Tocá **Reabrir con motivo**, escribí el motivo (al menos 10 caracteres) y tocá **Reabrir**.

Preceptor: desde la comisión, en **Inscriptos y asistencia**, tocá **Cierre** para consultar la planilla.

## Reglas

- El plazo de gracia es de 14 días corridos después del fin del período lectivo.
- El docente no puede confirmar si hay evaluaciones sin publicar. El aviso indica cuáles faltan.
- No se puede aprobar el cierre antes de la fecha de fin del período.
- Tampoco se puede aprobar si hay evaluaciones sin publicar. Si el docente no confirmó, además no puede haber alumnos pendientes de nota.
- Si el docente no confirmó, el botón es **Cerrar sin confirmación del docente** y el acta deja constancia de que no confirmó.
- Libro y folio son obligatorios (hasta 50 caracteres cada uno).
- Un cierre cerrado no se modifica. Solo se reabre, con motivo.
- Pagos y legajo se muestran como Sí o No, sin importes.
- Los alumnos dados de baja no cuentan en el resumen.
- Resultado del cierre con requisitos pendientes: se completa cuando se implemente #1638.

## Qué pasa después

- Al cerrarse el acta, los alumnos reciben un aviso con su resultado.
- Al reabrir, se avisa al docente y a los alumnos y queda auditado. El docente tiene que revisar y confirmar de nuevo, y el acta pasa a la versión siguiente al volver a cerrarse.
- Si el docente no confirmó, el sistema le recuerda a los 3 y a los 7 días del fin del período.
- Pasados los 14 días, si todas las notas están publicadas y no quedan alumnos pendientes de nota, la comisión se cierra sola.
- Si pasados los 14 días el cierre sigue pendiente, el sistema avisa a Dirección y a Administración, y le avisa al docente.

## Errores comunes

- Intentar confirmar con un parcial sin publicar: el botón queda deshabilitado y el aviso indica cuál.
- Querer cerrar una comisión cuyo período todavía no terminó: el sistema lo rechaza.
- Dejar vacío el libro o el folio: aparece «Cargá el libro y el folio donde se asienta el acta.».
- Reabrir con un motivo de menos de 10 caracteres.
- Tocar **Cierre** en una comisión sin ciclo: el botón aparece deshabilitado.

Pendiente de verificar: el Subdirector aprueba cierres en pantalla, pero la regla del servidor para aprobar nombra solo a Administrativo. Confirmar si el Subdirector puede aprobar.

Pendiente de verificar: la reapertura es solo del Director en el servidor. Confirmar que Administrativo y Subdirector no deben poder reabrir.
