# Arquitectura de módulos (documentación generada)

Todo lo que hay en esta carpeta (`components.puml`, `module-*.puml`, `module-*.adoc`) lo genera
Spring Modulith (`Documenter`) a partir de las clases compiladas. No se edita a mano: una edición
manual se pierde en la próxima regeneración y deja el diagrama desfasado respecto del código.

## Cómo se genera

Generador: `backend/src/test/java/com/red_escuela/backend/architecture/modulith/ModuleDocumentationTest.java`
(tag `docs`, `@SlowTest`). Escribe `components.puml` (vista de todos los módulos),
`module-<nombre>.puml` (vista de cada módulo y sus dependencias) y `module-<nombre>.adoc`
(canvas: paquete base, referencias a beans, eventos publicados y escuchados). Después de generar,
ordena las líneas `Rel(...)` de cada `.puml`, así que la salida es determinista: si la arquitectura
no cambió, no hay diff.

## Cómo regenerar

Por defecto el test escribe en `backend/target/modulith-docs/` (no toca el árbol). Para
actualizar lo versionado aquí, desde `backend/`:

```
./mvnw -o test -Dtest=ModuleDocumentationTest -Dtest.excludedGroups=none -Dmodulith.docs.update=true -Darchunit.freeze.store.default.allowStoreUpdate=false
```

Dos detalles que hacen que el comando no sea "silencioso":

- El test lleva `@Tag("docs")` y el pom excluye ese grupo por defecto (`test.excludedGroups=docs`);
  sin `-Dtest.excludedGroups=none` corre 0 tests y termina en `BUILD SUCCESS`. Verificar que el
  output diga `Tests run: 1`.
- La propiedad se pasa como `-Dmodulith.docs.update=true`: con `-DargLine=...` el flag no llegó al
  JVM del test y escribió en `target/modulith-docs/` en vez de en esta carpeta.
- El `Documenter` vacía la carpeta antes de escribir, así que borra este `README.md`: restaurarlo
  con `git checkout -- docs/architecture/README.md` antes de commitear.

Luego revisar el diff, commitear en una rama propia y abrir PR contra `develop`. El CI normal
ignora `docs/**`; el test de documentación corre en los pushes y en el nightly
(`.github/workflows/nightly-tests.yml`).

## Estado

Última regeneración versionada: 2026-10-28. Refleja M3a (`Institution` en `identity`) y billing v2.
