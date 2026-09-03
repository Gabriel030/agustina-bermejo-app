# Workflow — git, commits, PRs

## Regla no negociable #1: nunca commitear/pushear directo a `develop` ni `master`

**Todo cambio, sin importar cuán chico, va en su propia rama.** Nunca `git
commit` estando parado en `develop` o `master` — ni con aprobación del
usuario de por medio. Antes de tocar código: `git checkout develop && git
merge --ff-only origin/develop` (no asumir que el `develop` local está al
día) y recién ahí `git checkout -b feature/<slug-descriptivo>` (o
`refactor/<slug>` si es específicamente una refactorización sin feature
nueva). El commit y el push van sobre esa rama, nunca sobre `develop`/
`master` directo — así queda evidencia trazable y se puede revisar/revertir
sin tocar la rama de integración.

**Corrección real de esta sesión (2026-08-27)**: dos commits se hicieron
directo contra `develop` por apuro — el usuario lo marcó como error
explícito y pidió esta regla. No repetirlo aunque el cambio sea "chico" o
"solo documentación".

## Regla no negociable #2: aprobación antes de commit y antes de push

**Nunca correr `git commit` sin mostrar antes qué se va a commitear y recibir un
"sí" explícito del usuario para ese commit puntual.** Lo mismo para `git push` —
es una aprobación aparte de la del commit, aunque sea la misma conversación y el
mismo cambio. Que el usuario haya aprobado la tarea en general ("dale, empecemos",
"segui") no cuenta como aprobación del commit — hay que preguntar de nuevo cuando
el trabajo está listo para commitear.

Flujo esperado:
1. `git checkout develop && git merge --ff-only origin/develop` y
   `git checkout -b feature/<slug>` — nunca trabajar parado en `develop`.
2. Terminar la unidad de trabajo (una fase, un fix) y verificarla (tests, build,
   navegador si es frontend — ver [docs/context/testing.md](testing.md)).
3. Mostrar un resumen de los archivos tocados y el mensaje de commit propuesto.
4. Esperar aprobación explícita.
5. Commitear sobre la rama. Preguntar de nuevo, separado, antes de pushear.

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
