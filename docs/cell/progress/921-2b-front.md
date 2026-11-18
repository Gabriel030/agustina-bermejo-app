# 921#2b front — acciones de la lista de espera

## Criterios de aceptación
- [x] Menú por fila (Promover, Mover a primer lugar, Quitar) solo para Administrativo y Director
- [x] Promover con cupo antes → después; deshabilitado con tooltip si no es elegible o no hay lugar
- [x] Mover y Quitar con motivo de 10 a 500 caracteres y contador
- [x] Numeración entre prioritarios (`Prioridad manual N`)
- [x] Mocks MSW de promote, prioritize y remove
- [x] "Listas de espera" dentro de Inscripciones (ruta `inscripciones/listas-de-espera` y grupo del sidebar del Director)
- [x] Tests: requires reason before prioritizing, promote disabled when not eligible, menu entry lives under Enrollments

## Notas
- Hecho: vitest de `features/enrollment/pages` y `routes` en verde, lint sin errores, build verde. Sabotaje: sin `disableUntilMinLength` el test del motivo falla.
- Desvío: solo el Director tiene grupos de menú, por eso el grupo "Inscripciones" existe ahí; Preceptor y Administrativo conservan menú plano con la ruta nueva.
- No se verificó en navegador (claro/oscuro/móvil).
- Fuera de alcance: test de concurrencia e IT con comprobante (backend).
