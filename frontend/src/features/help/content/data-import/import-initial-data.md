---
title: Importar los datos iniciales
section: data-import
roles: [director]
status: borrador
screens: ["importacion-inicial", "importacion-inicial/nueva", "importacion-inicial/:id"]
last_review: 2026-11-28
---

## Para qué sirve

Para cargar de una sola vez la historia del instituto desde planillas: personas, carreras, materias, plan de estudios, correlativas, períodos, comisiones, inscripciones, cursadas, notas, asistencia y egresos.

La importación primero se revisa sin guardar nada. Recién cuando confirmás se escribe en el sistema.

## Quién puede

Solo el Director. Se entra desde el menú Institución, en «Importación inicial».

## Pasos

1. En «Importación inicial», tocá «Nueva importación».
2. Escribí un nombre para reconocerla después, por ejemplo «Carga inicial 2026». Tocá «Crear importación».
3. Vas a ver la lista de bloques. Cada bloque dice «Falta cargar» hasta que subas su archivo.
4. En cada bloque que vayas a cargar, tocá «Plantilla» para bajar el modelo. Completalo y guardalo como CSV.
5. Tocá «Subir archivo» y elegí el CSV. Si te equivocás, tocá «Reemplazar» y subí otro.
6. Cuando tengas los bloques que querés cargar, tocá «Revisar archivos». No hace falta cargar todos los bloques.
7. En la pantalla de revisión mirá los cuatro contadores: «Correctas», «Con error», «Ya existían» y «En conflicto».
8. Si hay errores, mirá la tabla «Qué corregir». Dice el archivo, la fila, la columna y qué está mal. Tocá «Descargar errores» para llevarte el listado.
9. Corregí el archivo en tu computadora. Tocá «Volver a los archivos», subí el archivo corregido con «Reemplazar» y volvé a tocar «Revisar archivos». Repetí hasta que no queden errores.
10. Con todo correcto, tocá «Importar». Se abre una ventana que resume cuántos registros se van a crear. Confirmá con «Importar».
11. Seguí el avance en pantalla. Podés salir: la importación sigue sola.
12. Al terminar vas a ver el reporte. Tocá «Descargar reporte».
13. Revisá los datos en el sistema. Si está todo bien, tocá «Cerrar la importación», escribí CERRAR y confirmá. El botón de cierre se habilita recién después de descargar el reporte.

## Reglas

- Cada archivo tiene que ser un CSV y pesar como máximo 5 MB.
- La importación tiene doce bloques: Personas, Carreras, Materias, Plan de estudios, Correlativas, Períodos lectivos, Comisiones, Inscripciones a carrera, Cursadas, Notas por examen y parcial, Asistencia por clase y Egresos.
- En Personas, el mail es obligatorio y no puede repetirse.
- En Egresos, la inscripción tiene que estar cargada como completada.
- «Importar» está bloqueado mientras haya filas con error o en conflicto.
- Al importar, la facturación no se toca (tiene que estar apagada) y no se envía ningún aviso ni mail.
- Se importa por partes y no se puede pausar.
- Mientras la importación no esté cerrada, se puede deshacer. Ver «Deshacer una importación».

## Qué pasa después

- Una importación puede quedar en estos estados: «Borrador», «Revisando», «Listo para importar», «Con errores», «Importando», «Importado», «Importada en parte», «Falló», «Cerrada», «Deshaciendo» y «Deshecha».
- Si quedó «Importada en parte», una parte de las filas no se pudo importar. Tocá «Reintentar lo que falta»: sigue desde donde quedó y no duplica nada.
- Si hay egresos que quedaron pendientes de revisión del Director, el reporte lo avisa. Esos egresos no habilitan certificado hasta que se revisen.
- Al cerrar la importación queda como constancia, ya no se puede deshacer ni subir archivos.
- Las importaciones quedan registradas en la Auditoría.

## Errores comunes

- «El archivo tiene que ser un CSV»: guardá la planilla en formato CSV, partiendo de la plantilla que baja el botón «Plantilla».
- «El archivo es demasiado grande»: el máximo es 5 MB. Dividí la carga en archivos más chicos.
- «Importar» no se habilita: todavía quedan filas con error o en conflicto. Corregilas y volvé a revisar.
- No se puede cerrar la importación: falta descargar el reporte.
- Un mail repetido en Personas marca error. Cada persona tiene que tener un mail propio.

**Pendiente de verificar:**

- El texto de cada error y el formato exacto de columnas de cada plantilla los define el servidor; esta guía no los lista.
- Si se puede importar más de una vez con las mismas personas (por ejemplo, filas «Ya existían») depende del servidor y no está confirmado en esta guía.
