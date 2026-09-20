# CHECKPOINTS.md — Criterios objetivos de "feature done"

> Todos los items deben estar en verde antes de pasar al Gate 3.
> Son binarios: pasa o no pasa. No hay "casi".

---

## CI (obligatorio — verificado por el Líder en Fase 4)

- [ ] Job `backend` verde en GitHub Actions (`./mvnw clean verify` — incluye Karate)
- [ ] Job `frontend` verde en GitHub Actions (lint + build + `npm run test`)

---

## Tests locales (verificado por QA en Fase 3)

- [ ] `cd backend && ./mvnw test -q` termina sin fallos
- [ ] `cd frontend && npm run lint` sin errores
- [ ] `cd frontend && npm run build` sin errores
- [ ] `cd frontend && npm run test` sin fallos

---

## Implementación completa (verificado por QA leyendo los status files)

- [ ] Todos los endpoints listados en `1-plan.md` → Fase Backend están en `backend-status.md` como ✅
- [ ] Todas las pantallas/componentes listados en `1-plan.md` → Fase Frontend están en `frontend-status.md` como ✅
- [ ] `gaps.md` no tiene gaps con `Blocker: SI` sin resolver

---

## Seguridad backend (verificado por adversarial-backend)

- [ ] Cada endpoint nuevo tiene `@PreAuthorize` con el rol correcto
- [ ] Cada `@RequestBody` nuevo tiene `@Valid`
- [ ] No hay queries nativas con concatenación de strings (solo `?1`/`:param`)
- [ ] No hay endpoint que devuelva una entidad JPA directa (siempre DTO con `from()`)
- [ ] Los errores de negocio devuelven 4xx, no 500

---

## Calidad frontend (verificado por adversarial-frontend)

- [ ] La pantalla tiene estado de carga (skeleton o spinner)
- [ ] La pantalla tiene estado vacío (cuando la lista está vacía)
- [ ] La pantalla maneja error 500 del backend sin explotar
- [ ] La pantalla se ve correctamente en viewport 375px (Capacitor target)
- [ ] Inputs numéricos rechazan texto, campos requeridos no aceptan vacío

---

## Calidad de código (verificado por code-reviewer)

- [ ] Ningún archivo nuevo tiene comentarios
- [ ] Todos los identificadores nuevos están en inglés
- [ ] No hay N+1 queries (colecciones lazy accedidas en loops)
- [ ] No hay contradicciones entre `0-spec.md` y la implementación real

---

## Cierre de feature (verificado por Líder en Fase 4)

- [ ] `_surface/backend-surface.md` actualizado con los endpoints nuevos
- [ ] `_surface/frontend-surface.md` actualizado con pantallas/componentes nuevos
- [ ] Feature marcada `done` en `features/feature_list.json`
- [ ] `progress.md` muestra todas las fases como ✅

---

## Circuit breaker

Si el loop de corrección de Fase 3 supera **3 iteraciones** sobre el mismo blocker,
el agente para y escala al humano con un resumen del problema en `qa-report.md`.
No seguir girando en loop indefinido.
