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
actualizar lo versionado aquí:

```
./mvnw -f backend/pom.xml test -Dtest=ModuleDocumentationTest -DargLine=-Dmodulith.docs.update=true
```

Luego revisar el diff, commitear en una rama propia y abrir PR contra `develop`. El CI normal
ignora `docs/**`; el test de documentación corre en los pushes y en el nightly
(`.github/workflows/nightly-tests.yml`).

## Estado

Última regeneración versionada: 2026-10-21 (commit `27c33fd1`). Quedó desactualizada respecto de
cambios posteriores, entre ellos:

- M3a (reubicación de clases): `Institution` pasó de `academicstructure` a `identity`,
  `FinalGradeType` a `enrollment`, `NotOwnSectionException` a `academicstructure`. Los canvases
  siguen mostrando `c.r.b.a.entity.Institution` y `c.r.b.a.repository.InstitutionRepository`
  como pertenecientes a Academic structure.
- Billing v2: reembolsos, autorizaciones, descuentos, recordatorios, collections, reporting
  (subpaquetes `authorization`, `collections`, `discount`, `pricing`, `reporting`, etc. del módulo
  `billing`) y las dependencias entre módulos que introdujeron.

Pendiente: regenerar con el comando de arriba cuando haya un slot de Maven libre. La lista de
módulos de primer nivel no cambió (academicrecord, academicstructure, announcement, attendance,
billing, config, enrollment, evaluation, graduation, identity, notification, shared), por lo que
no se esperan archivos nuevos, solo cambios de contenido.
