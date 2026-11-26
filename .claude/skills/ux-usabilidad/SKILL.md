---
name: ux-usabilidad
description: Criterio de usabilidad, UX y CX para diseñar, mockear o revisar pantallas y navegación de la app (catálogo, menús, filtros, flujos). Prioriza una sola forma de hacer cada cosa, cero redundancia, fluidez y bajo esfuerzo del usuario. Usar antes de armar un mockup, al revisar uno, y cuando Gabriel diga «usabilidad», «UX», «CX», «redundante», «fluidez» o rechace un diseño por usabilidad.
---

# Usabilidad primero (skill de la célula)

Obligatoria para el líder de frontend y para quien arma o revisa un mockup. Se aplica ANTES de dibujar y ANTES de mostrar a Gabriel. Complementa `docs/context/frontend.md`, `docs/context/components.md` y `docs/context/design-system.md`; no los reemplaza.

## Por qué existe
Gabriel rechazó varios mockups del catálogo por usabilidad: un desplegable «horrible», un menú y debajo las mismas categorías repetidas, una barra de pestañas que duplicaba el menú superior, un filtro con «quitar filtro» tipo planilla («esto no es un Excel»). La causa común: se diseñó con convenciones genéricas sin partir de la tarea de la persona ni de cómo lo resuelven productos de referencia.

## Reglas duras (un mockup que las rompe no se muestra)
1. **Una sola navegación.** Una función se alcanza por UN camino principal. Prohibido: pestañas de página que repiten ítems del menú; el mismo selector (por ejemplo áreas o tipos) en dos lugares; sublinks que duplican la barra. Antes de entregar, listá todos los controles de navegación y filtro de la pantalla y tachá los que repitan a otro.
2. **Cero redundancia de contenido.** Cada dato aparece una vez en el primer vistazo. Si un bloque repite lo que ya dice otro (cuadrados de categorías + lista de las mismas categorías), se queda el que lleva más rápido a la acción y se saca el otro.
3. **La persona entra con una tarea, no con una estructura.** Escribí primero la tarea en una frase («quiero ver qué carreras de diseño hay y anotarme») y contá los pasos hasta lograrla: tiene que ser el mínimo posible. Cada paso, campo o control que no sirve a esa tarea se elimina o se mueve al detalle.
4. **Fluidez.** Sin callejones sin salida, sin pasos de confirmación innecesarios, sin recargar contexto: lo elegido se ve reflejado en el lugar (la selección en el menú queda marcada, el listado cambia, el título dice dónde estás). Volver atrás siempre es posible y predecible.
5. **Filtros y selección con lenguaje natural, no de planilla.** Nada de chips con «x» ni «quitar filtro» como mecanismo principal. El estado actual se expresa como camino o título («Oferta académica › De grado › Diseño») con migas de pan clicables, y «ver toda la oferta» como salida. Los filtros aplicados se leen como una frase, no como una fila de etiquetas.
6. **Menús de navegación al estilo de los sitios de referencia.** Menús desplegables en la barra superior que se abren por hover/clic/teclado y permiten ir bajando por niveles hasta elegir; en móvil, lista por niveles con «Volver». No usar selects nativos ni desplegables sueltos para navegar.
7. **Esfuerzo mínimo, errores baratos.** Valores por defecto razonables, validación en el lugar, mensajes que dicen cómo arreglar el problema, y todo lo destructivo o que mueve plata con confirmación explícita y reversible cuando se pueda.
8. **Lo que no existe no se dibuja.** Todo ítem de menú, botón o destino del mockup tiene que existir en la app o estar en el alcance del ticket; si no, se marca como «nuevo» y se pide aparte (ejemplos: una barra utilitaria con destinos inexistentes).
9. **Sin precios ni datos comerciales en listados públicos** salvo decisión expresa de Gabriel (decisión del 26-nov: el precio va al contactar o en el detalle).

## Proceso obligatorio para un mockup
1. **Tarea y persona:** quién usa la pantalla (rol), qué quiere lograr, cuántos pasos hoy y cuántos con el diseño.
2. **Referencias reales:** mirar al menos 3 productos que resuelven lo mismo (para oferta académica: universidades como Palermo, UTN, Belgrano, UADE, UTDT; para paneles: productos de gestión escolar) y anotar CÓMO lo resuelven (estructura, niveles, qué muestran y qué esconden). Si la sesión no puede abrir esos sitios, pedírselo a Gabriel (capturas) o a coordinador-6 antes de diseñar; no inventar a partir de «convenciones comunes».
3. **Mapa de navegación y controles** de la pantalla: lista de todo lo clicable; aplicar las reglas 1 y 2 y dejar el resultado escrito en el Artifact.
4. **Estados:** con datos, vacío, cargando, error, sin permiso, sin resultados, un solo resultado, muchos resultados; móvil 390 px y escritorio 1280 px; claro y oscuro.
5. **Prueba de 5 segundos y de la tarea:** ¿se entiende qué es y qué hacer? ¿la tarea sale en el mínimo de pasos?
6. **Revisión independiente** con el subagente `ux-advisor` (contexto limpio) pasándole esta skill: por pedido de Gabriel (26-nov) los mockups de navegación, catálogo y pantallas nuevas VUELVEN a pasar por `ux-advisor` antes de mostrarse, como excepción puntual a la regla 20 solo para mockups. Un mockup `RECHAZADO` no se muestra.
7. **Mostrar a Gabriel** como Artifact, con: la tarea, los pasos contados, las referencias usadas y qué se eliminó por redundante.

## Checklist de revisión (para `ux-advisor` y para el líder)
- [ ] ¿Hay dos controles que llevan al mismo lugar o muestran el mismo conjunto? (hallazgo IMPORTANTE o BLOQUEANTE)
- [ ] ¿Algún texto, botón o destino que no existe en la app?
- [ ] ¿La selección actual se ve y se puede deshacer sin controles tipo planilla?
- [ ] ¿La tarea principal sale en el mínimo de pasos? Contarlos.
- [ ] ¿Hay estado vacío, error, cargando, sin resultados y sin permiso?
- [ ] ¿En móvil se resuelve sin hover y sin scroll horizontal?
- [ ] ¿Contraste, foco visible y teclado (menús navegables con flechas, Esc cierra)?
- [ ] ¿Textos en castellano rioplatense, accionables y sin jerga?
- [ ] ¿Se respeta el design system (tokens, componentes) y se declara lo nuevo?
- [ ] ¿Hay algo que se pueda sacar sin perder la tarea? Sacarlo.

## Anti-patrones vistos (no repetir)
- Pestañas internas con los mismos nombres que el menú de arriba.
- Un menú de categorías y, debajo, la misma grilla de categorías.
- Chips «x» / «Quitar filtro» como control principal de filtrado.
- Desplegable nativo para elegir tipo de oferta.
- Barra utilitaria con enlaces a pantallas que no existen.
- Precio en tarjetas de catálogo.
- Diseñar «por convención» sin haber visto cómo lo resuelven las referencias.
