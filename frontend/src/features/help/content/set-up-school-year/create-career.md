---
title: Crear una carrera
section: set-up-school-year
roles: [administrativo, subdirector]
status: borrador
screens: ["gestion-academica/carreras/nueva", "gestion-academica/carreras/:id/editar"]
last_review: 2026-11-28
---

## Para qué sirve

Da de alta una carrera con sus datos básicos, su duración y sus valores de cuota y matrícula. Después se le arma el plan de estudios y se le abren cohortes.

## Quién puede

Administrativo y Subdirector. Entran por «Gestión académica».

## Pasos

1. Entrá a «Gestión académica» y elegí la solapa «Carreras».
2. Tocá «Nueva carrera».
3. En el paso «Datos básicos» completá el «Nombre de la carrera» y la «Duración (años)». Si querés, elegí el «Nivel» (Pregrado, De grado, Posgrado, Extensión o Diplomatura) y la «Modalidad» (Presencial, Virtual o Híbrido).
4. Tocá «Continuar».
5. En el paso «Contenido académico» completá «Créditos requeridos», «Cuota mensual» y «Matrícula». Podés sumar «Horas requeridas», «Perfil del egresado» y «Requisitos de admisión».
6. Tocá «Crear carrera». Aparece «Carrera creada» y se abre la ficha de la carrera.

Para corregir datos más adelante: abrí la carrera y tocá «Editar».

Otras acciones de la ficha:

- «Desactivar» y «Activar» cambian el estado de la carrera. Desactivar pide confirmación.
- «Publicar en catálogo» y «Retirar del catálogo» controlan si la carrera aparece para inscribirse. Al retirarla, los alumnos actuales no se ven afectados.

## Reglas

- La duración es un número entero de 1 a 10 años.
- Los créditos son un número entero, de 0 en adelante.
- La cuota y la matrícula tienen que ser mayores que cero. Para ofrecer algo gratis se usa un descuento, no un valor en cero.
- Las horas, si se cargan, son un número entero de 0 en adelante.
- Los precios se cargan en pesos.
- Una carrera nueva todavía no tiene materias: hay que armar el plan antes de abrir una cohorte.

## Qué pasa después

Se abre la ficha de la carrera, con las solapas «Plan de estudios», «Años lectivos» y «Cohortes». El siguiente paso es armar el plan. Ver «Versionar el plan de estudios».

## Errores comunes

- «Continuar» marca campos en rojo: falta el nombre o la duración, o la duración no está entre 1 y 10.
- «Crear carrera» marca campos en rojo: faltan créditos, cuota o matrícula, o están en cero.
- Retirar del catálogo una carrera que se quiere seguir ofreciendo: se revierte con «Publicar en catálogo».

**Pendiente de verificar:** en qué estado queda la carrera recién creada (activa o inactiva) y si «Activar» alcanza para dejarla lista para inscribir. No se confirmó en el código.
