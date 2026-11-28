---
title: Abrir una cohorte
section: set-up-school-year
roles: [administrativo, subdirector]
status: borrador
screens: ["gestion-academica/carreras/:id"]
last_review: 2026-11-28
---

## Para qué sirve

Una cohorte es el grupo de ingresantes de una carrera en un semestre y un año. Abrirla habilita la inscripción de ingresantes desde la fecha de admisión. Se crea con su responsable y su ventana de admisión, y se abre cuando el checklist «Para abrir» está completo.

## Quién puede

Administrativo y Subdirector. Entran desde «Gestión académica», abriendo la carrera y yendo a la solapa «Cohortes».

El responsable de la cohorte es un preceptor. Los preceptores de cada comisión los asigna Dirección. Ver «Crear comisiones y asignar preceptores».

## Pasos

Crear la cohorte:

1. Entrá a «Gestión académica», solapa «Carreras», y abrí la carrera.
2. Elegí la solapa «Cohortes» y tocá «Nueva cohorte». También podés usar «Nueva cohorte» en «Acciones frecuentes», arriba de «Gestión académica»: te pide elegir la carrera y tocar «Continuar».
3. En «Año lectivo» elegí el año. Cada opción muestra el año y la cantidad de cuotas.
4. En «Semestre de ingreso» elegí «1er semestre» o «2do semestre».
5. Completá «Inicio admisión», «Fin admisión» e «Inicio de cursada».
6. En «Responsable» tocá «Buscar preceptor», buscá por nombre y elegí al menos uno. Es el preceptor de toda la cohorte.
7. Si hace falta, cargá el «Descuento de la matrícula de ingreso (%)». Vacío, usa el valor por defecto.
8. Tocá «Crear cohorte». Aparece «Cohorte creada».

Completar el checklist y abrirla:

1. En «Cohortes» mirá la tarjeta de la cohorte. Una etiqueta indica «Incompleta», «Lista para abrir» o «Abierta».
2. En el bloque «Para abrir» se ve «N de M» y los seis puntos: Responsable, Plan de estudios, Año lectivo, Comisiones de primer año, Preceptores en comisiones y Feriados cargados. Los que faltan llevan un signo de advertencia.
3. Si falta el responsable, tocá «Completar» al lado de «Responsable». Se abre «Responsable de la cohorte»: elegí al menos un preceptor y tocá «Guardar».
4. Si faltan preceptores en comisiones, la línea dice cuántas comisiones ya tienen preceptor, por ejemplo «2 de 5». Tocá «Ir a comisiones» para asignarlos.
5. Cuando todo está completo, el botón «Abrir» se habilita solo. Tocalo.
6. En el cuadro «Abrir» con el nombre de la cohorte, tocá «Abrir cohorte».

## Reglas

- El responsable es obligatorio. Sin al menos un preceptor, la pantalla marca «Elegí al menos un preceptor.» y no crea la cohorte.
- La cohorte se puede abrir solo con los seis puntos del checklist en regla:
  - Responsable: la cohorte tiene al menos un preceptor responsable.
  - Plan de estudios: el plan de la cohorte está vigente.
  - Año lectivo: la cohorte tiene su año lectivo de cobro. Este punto se controla solo si la facturación está activa.
  - Comisiones de primer año: cada materia del primer año del plan tiene al menos una comisión aprobada en el período de la cohorte, y cada una de esas comisiones tiene docente titular, cupo, horario y aula, y evaluación aprobada.
  - Preceptores en comisiones: cada una de esas comisiones aprobadas tiene al menos un preceptor.
  - Feriados cargados: hay feriados cargados para el año del período.
- «Abrir» está deshabilitado mientras falte algún punto.
- No se puede crear dos veces la misma cohorte: misma carrera, mismo año lectivo y mismo semestre de ingreso.
- Para crear una cohorte, la carrera tiene que tener un año lectivo cargado. Si no, el formulario avisa que Dirección tiene que cargarlo antes y «Crear cohorte» queda deshabilitado.
- Descuento de la matrícula: si lo dejás vacío es 50% para el 2do semestre y sin descuento para el 1er semestre. Si alguien que no es Director carga otro valor, la cohorte se crea con el valor por defecto y el pedido queda como propuesta para que el Director lo resuelva.
- Una cohorte ya abierta no se vuelve a abrir.

## Qué pasa después

La cohorte pasa a «Abierta» y el bloque «Para abrir» desaparece. Se habilita la inscripción de ingresantes desde la fecha de admisión. La tarjeta muestra el responsable, el plan y las fechas de admisión y de cursada.

## Errores comunes

- «No pudimos abrir la cohorte. No se hizo ningún cambio. Probá de nuevo en un momento.»: el sistema volvió a controlar y todavía falta algo. Mirá el checklist.
- «No se pudo crear la cohorte.»: es el único mensaje que muestra la pantalla ante cualquier falla. Revisá que no exista ya una cohorte igual y que la carrera tenga año lectivo cargado.
- «Completá todos los campos.»: falta alguna fecha, el año lectivo o el semestre.
- Querer abrir sin haber asignado preceptores a las comisiones: el punto «Preceptores en comisiones» queda pendiente hasta que Dirección los asigne.
- Querer abrir sin haber aprobado antes el ciclo de la oferta: sin comisiones aprobadas, «Comisiones de primer año» no se completa. Ver «Aprobar la oferta del ciclo».

**Pendiente de verificar:**
- El enlace «Ir a comisiones» lleva a la «Oferta académica» de Dirección. Para Administrativo o Subdirector esa pantalla puede no estar disponible. Hay que confirmar con Gabriel cómo debe resolverse.
- El backend también permite gestionar cohortes al Director, pero la app no le da la pantalla de cohortes. No se describe acá.
- Con la facturación apagada, la creación de cohortes depende de otro circuito que la pantalla actual no usa. No se describe.
- Qué mensaje exacto ve quien intenta crear una cohorte duplicada: la pantalla muestra solo el mensaje genérico.
- Dónde resuelve el Director la propuesta de descuento de la matrícula. Es cobranza y queda fuera de esta rebanada.
