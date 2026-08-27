# Workflow — git, commits, PRs

## Regla no negociable: aprobación antes de commit y antes de push

**Nunca correr `git commit` sin mostrar antes qué se va a commitear y recibir un
"sí" explícito del usuario para ese commit puntual.** Lo mismo para `git push` —
es una aprobación aparte de la del commit, aunque sea la misma conversación y el
mismo cambio. Que el usuario haya aprobado la tarea en general ("dale, empecemos",
"segui") no cuenta como aprobación del commit — hay que preguntar de nuevo cuando
el trabajo está listo para commitear.

Flujo esperado:
1. Terminar la unidad de trabajo (una fase, un fix) y verificarla (tests, build,
   navegador si es frontend — ver [docs/context/testing.md](testing.md)).
2. Mostrar un resumen de los archivos tocados y el mensaje de commit propuesto.
3. Esperar aprobación explícita.
4. Commitear. Preguntar de nuevo, separado, antes de pushear.

## Ramas

`develop` es la base de integración (no `master` directo). Cada feature/fase sale
de `develop` actualizada (`git checkout develop && git merge --ff-only
origin/develop` antes de ramificar — no asumir que el `develop` local está al día,
puede estar desactualizado aunque el remoto ya tenga todo mergeado). Nombre de
rama: `feature/<slug-descriptivo>` en general; `refactor/<slug>` si es
específicamente una refactorización sin feature nueva.

## Commits

Mensaje en español, describe el "por qué" no solo el "qué" (ver
`~/.claude/CLAUDE.md` del usuario — regla general de todos sus proyectos, no
específica de este repo). Un commit por unidad de trabajo coherente, no un commit
gigante por fase completa si la fase tuvo sub-pasos claramente separables (ej. el
fix de backend de un gap encontrado a mitad de una feature de frontend puede ir
en su propio commit si tiene sentido revisarlo aparte).

## Pull Requests

Se abren contra `develop`. Título corto, cuerpo con qué cambia y plan de
verificación (mismo formato que usa `gh pr create` por default en este entorno).
Nunca se abre un PR sin que el usuario lo haya pedido explícitamente — mismo
principio que el commit/push de arriba, un nivel más arriba en la cadena.
