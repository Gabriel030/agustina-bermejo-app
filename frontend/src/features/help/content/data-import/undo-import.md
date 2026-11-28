---
title: Deshacer una importación
section: data-import
roles: [director]
status: borrador
screens: ["importacion-inicial/:id/deshacer"]
last_review: 2026-11-28
---

## Para qué sirve

Para borrar todo lo que creó una importación cuando se cargó algo mal. Lo que ya existía en el sistema antes de la importación no se toca.

## Quién puede

Solo el Director.

## Pasos

1. En «Importación inicial», abrí la importación que querés deshacer.
2. En el reporte, buscá el recuadro «Mientras no la cierres» y tocá «Deshacer esta importación…».
3. En «Deshacer la importación» mirá las dos listas. «Se va a borrar» muestra cuántos registros de cada tipo se eliminan. «Se conserva» muestra lo que queda.
4. Si aparece «No se puede deshacer: hay actividad posterior», leé la lista «Qué lo impide». No se borró nada. Hay que resolver esos motivos antes de volver a intentar.
5. Si se puede, tocá «Deshacer la importación».
6. En la ventana de confirmación, escribí DESHACER y tocá «Deshacer la importación».
7. Cuando termina, aparece el aviso «Importación deshecha.».

## Reglas

- Solo se puede deshacer una importación en estado «Importado», «Importada en parte» o «Falló».
- Una importación «Cerrada» ya no se puede deshacer.
- Se borra todo o nada.
- Las carreras, materias, plan de estudios y correlativas se conservan siempre, porque son la estructura académica.
- Si alguien ya trabajó sobre los datos importados, no se puede deshacer. La pantalla muestra qué lo impide, con el tipo de dato y su identificación.
- Hace falta escribir DESHACER para confirmar.

## Qué pasa después

- La importación queda en estado «Deshecha». Lo que había creado se borró y la estructura académica se conserva.
- El intento queda registrado en la Auditoría, incluso cuando se bloquea.

## Errores comunes

- «Esta importación ya no se puede deshacer»: está cerrada, deshecha o en un estado que no lo permite.
- Querer deshacer cuando ya se cargaron notas o asistencia sobre los datos importados: la pantalla lo bloquea y muestra los motivos.
- Esperar que se borren las carreras o materias: se conservan a propósito.
**Pendiente de verificar:**

- Qué situaciones concretas cuentan como «actividad posterior» las decide el servidor; esta guía no las enumera.
