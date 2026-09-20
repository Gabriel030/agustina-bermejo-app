# HARNESS.md — Mapa de navegación para agentes

> Punto de entrada obligatorio para cualquier agente que trabaje en este proyecto.
> Lee solo lo que necesités cuando lo necesités (divulgación progresiva).

---

## 1. Antes de empezar (obligatorio)

1. Ejecutá `.claude/harness/scripts/init.sh` y verificá que termina sin errores.
2. Leé `features/feature_list.json` — elegí la tarea con estado `pending`.
   **Una sola feature a la vez. No mezclés cambios de varias.**
3. Leé `features/{slug}/progress.md` si existe — la sesión anterior dejó contexto.
   Retomá desde el último paso completado, no rehagas lo que ya está.
4. Si sos el **Líder**: leé además `.claude/agents/leader.md` completo.

---

## 2. Mapa de archivos

| Archivo | Qué contiene | Cuándo leerlo |
|---|---|---|
| `features/feature_list.json` | Cola de features con estado y épica | Siempre al empezar |
| `features/{slug}/progress.md` | Fase activa, qué terminó, próximo paso | Siempre al empezar |
| `features/{slug}/0-spec.md` | Spec aprobada por el humano | Antes de planificar |
| `features/{slug}/ux-decisions.md` | Mockup aprobado + decisiones UX | Antes de implementar FE |
| `features/{slug}/1-plan.md` | Plan técnico del Líder (fuente de verdad) | Todos los agentes |
| `features/{slug}/backend-status.md` | Log del agente backend + endpoints creados | QA, FE (para integración) |
| `features/{slug}/frontend-status.md` | Log del agente frontend + pantallas creadas | QA |
| `features/{slug}/gaps.md` | Gaps descubiertos en el medio (append-only) | Líder (monitorea) |
| `features/{slug}/qa-report.md` | Reporte consolidado fase 3 | Líder antes de Gate 3 |
| `.claude/harness/CHECKPOINTS.md` | Criterios objetivos y binarios de "done" | QA, siempre |
| `features/_surface/backend-surface.md` | Endpoints existentes, acumulativo por feature | Líder, Backend |
| `features/_surface/frontend-surface.md` | Pantallas/componentes existentes, acumulativo | Líder, Frontend |
| `features/{epica}/epic-context.md` | Contexto de la épica + dependencias cross-feature | Líder al planificar |
| `docs/context/backend.md` | Convenciones Spring Boot de este proyecto | Backend, Líder |
| `docs/context/frontend.md` | Convenciones React de este proyecto | Frontend, UX |
| `docs/context/testing.md` | Cómo testear backend y frontend | Backend, Frontend, QA |
| `docs/context/workflow.md` | Reglas de git, commits, PRs | Líder antes de commitear |
| `.claude/agents/` | Instrucciones de cada agente | Cuando orquestás subagentes |

---

## 3. Fases de una feature

```
[Humano describe la feature en 0-spec.md]
         ↓
FASE 0 — UX/UI  (agente: ux-designer)
  Produce: ux-decisions.md + Artifact de mockup
         ↓
[GATE 1] Humano aprueba el mockup
         ↓
FASE 1 — Planificación  (agente: leader)
  Produce: 1-plan.md + progress.md
         ↓
[GATE 2] Humano aprueba el plan
         ↓
FASE 2 — Implementación en paralelo
  ┌─────────────────────────┐  ┌─────────────────────────┐
  │  backend                │  │  frontend               │
  │  → backend-status.md   │  │  → frontend-status.md   │
  │  → gaps.md si falta BE │  │  → gaps.md si falta FE  │
  └─────────────────────────┘  └─────────────────────────┘
  (el frontend arranca con datos mock del contrato en 1-plan.md)
         ↓ gap descubierto → mini-ciclo backend → frontend reanuda
FASE 3 — Review en paralelo
  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
  │  code-reviewer   │  │  adversarial-be  │  │  adversarial-fe  │
  └──────────────────┘  └──────────────────┘  └──────────────────┘
  → todos escriben secciones en qa-report.md
         ↓
  ¿Hay blockers? → backend/frontend corrigen → volver a Fase 3
  ¿Loop > 3 veces? → escalar al humano (circuit breaker)
         ↓
[GATE 3] Humano aprueba qa-report
         ↓
FASE 4 — Cierre  (agente: leader)
  → Commit + push (aprobación explícita del humano)
  → PR contra develop (aprobación explícita del humano)
  → Loop CI hasta ambos jobs verdes (delegación pre-aprobada)
  → Actualiza _surface/ con lo nuevo
  → Marca feature done en feature_list.json
         ↓
[GATE 4] Humano revisa PR → merge a develop
```

---

## 4. Reglas duras

- **Una sola feature a la vez.** No mezclés cambios de varias tareas en una misma sesión.
- **Nunca commitear a `develop` o `master` directo.** Siempre rama `feature/<slug>`.
- **Aprobación explícita del humano** antes de cada commit y antes de cada push, excepto
  commits de fix de CI dentro de un loop CI ya pre-aprobado para esa feature.
- **No declarés una fase terminada** sin haber escrito su archivo de output.
- **Actualizá `progress.md` al terminar cada paso significativo**, no solo al final.
- **Si encontrás un gap**, escribilo en `gaps.md` inmediatamente antes de continuar o parar.
- **Identificadores de código en inglés** — clases, métodos, variables, endpoints. Sin excepciones.
- **Cero comentarios** en código nuevo (ni explicativos, ni javadoc, nada).

---

## 5. Protocolo de gap

Cuando un agente necesita algo que no existe (endpoint, componente, entidad):

```markdown
## Gap — {timestamp ISO}
- **Qué falta:** descripción concreta (ej: `GET /api/subjects/{id}/sections`)
- **Por qué:** contexto de por qué lo necesitás
- **Contrato esperado:** { campos, tipos }
- **Blocker:** SI / NO
```

- `Blocker: SI` → parate. El Líder crea sub-feature `{id}.{n}` en feature_list.json
  y prioriza el mini-ciclo de backend antes de que frontend continúe.
- `Blocker: NO` → continuá con datos mock. Señalá en `frontend-status.md`
  qué queda pendiente de integrar cuando el endpoint esté listo.
