---
proyecto: agustina-bermejo-app
tipo: spec técnica
épica: 10
fase: 10.1
estado: listo para implementar
---

# Fase 10.1 — Backend: dominio y gaps

## Depende de

Nada. Esta fase va primero porque cierra los gaps del modelo que las demás fases de la épica necesitan.

Al terminar queda pendiente: los endpoints de Subject, Program y Course ya existen. Esta fase solo agrega campos y un enum; no crea nuevos endpoints salvo los de visibilidad del catálogo.

---

## Flujos de usuario

No hay flujos de usuario directos en esta fase — es infraestructura de datos. El impacto visible es que los endpoints de Subject y Program devuelven campos nuevos, y que el plan de estudios puede categorizarse.

**Criterios de aceptación:**

- Dado un `POST /subjects` con `weeklyHours: 4`, el subject se guarda con 4 horas semanales y el `GET /subjects/{id}` las devuelve.
- Dado un `POST /programs` con `totalHoursRequired: 2400`, el program lo persiste y lo devuelve en GET.
- Dado un `POST /programs/{id}/subjects` con `category: MANDATORY`, `curricularYear: 1`, `curricularTerm: FIRST`, el vínculo se guarda correctamente.
- Dado un `POST /programs/{id}/subjects` con `category: ADDITIONAL_MANDATORY`, los campos `curricularYear` y `curricularTerm` quedan null.
- Dado un `POST /programs/{id}/subjects` con `category: ELECTIVE` pero sin `electiveGroupId`, el endpoint devuelve 400.
- Dado un `PUT /programs/{id}/catalog-visibility` con `{ "visible": true }`, el campo `visible_in_catalog` del program pasa a `true`.
- Dado un `GET /programs/{id}`, el campo `visibleInCatalog` aparece en la respuesta.

---

## Patrón de diseño

Use Case (`XxxUseCase` + `@UseCase XxxService`). Aplica solo para los dos nuevos use cases de visibilidad de catálogo. Los cambios a Subject y ProgramSubject son extensiones de use cases existentes.

---

## Cambios de entidades

### Subject — nuevo campo

```java
@Column(name = "weekly_hours")
private Integer weeklyHours;
```

### Program — nuevo campo

```java
@Column(name = "total_hours_required")
private Integer totalHoursRequired;

@Column(name = "visible_in_catalog", nullable = false)
private boolean visibleInCatalog = false;
```

### ProgramSubjectCategory — enum nuevo

```java
// package: com.red_escuela.backend.academicstructure.entity
public enum ProgramSubjectCategory {
    MANDATORY,
    ADDITIONAL_MANDATORY,
    ELECTIVE
}
```

### ProgramSubject — nuevo campo

```java
@Enumerated(EnumType.STRING)
@Column(name = "category", nullable = false)
private ProgramSubjectCategory category;
```

**Invariantes a validar en el use case:**
- Si `category = MANDATORY`: `curricularYear` y `curricularTerm` requeridos; `electiveGroupId` null.
- Si `category = ADDITIONAL_MANDATORY`: `curricularYear` y `curricularTerm` deben ser null; `electiveGroupId` null.
- Si `category = ELECTIVE`: `electiveGroupId` requerido; `curricularYear` y `curricularTerm` deben ser null.

### Course — mismo visibleInCatalog

```java
@Column(name = "visible_in_catalog", nullable = false)
private boolean visibleInCatalog = false;
```

---

## DTOs afectados

### SubjectResponse — agregar campo

```java
public record SubjectResponse(
    Long id, String name, String code, String description,
    Integer weeklyHours,
    Integer maxFinalExamAttempts, String status) { ... }
```

### CreateSubjectRequest / UpdateSubjectRequest — agregar campo

```java
record CreateSubjectRequest(
    @NotBlank String name,
    @NotBlank @Size(min=1, max=4) String code,
    String description,
    Integer weeklyHours,
    Integer maxFinalExamAttempts) {}
```

### ProgramResponse — agregar campos

```java
public record ProgramResponse(
    Long id, String name, Integer totalYears,
    BigDecimal price, BigDecimal enrollmentFeeAmount,
    String status, Integer totalCreditsRequired,
    Integer totalHoursRequired,
    boolean visibleInCatalog) { ... }
```

### CreateProgramRequest / UpdateProgramRequest — agregar campos

```java
record CreateProgramRequest(
    @NotBlank String name,
    @NotNull Integer totalYears,
    BigDecimal price,
    BigDecimal enrollmentFeeAmount,
    Integer totalCreditsRequired,
    Integer totalHoursRequired) {}
```

### CreateProgramSubjectRequest — agregar campo category

```java
record CreateProgramSubjectRequest(
    @NotNull Long subjectId,
    @NotNull ProgramSubjectCategory category,
    Integer curricularYear,
    CurricularTerm curricularTerm,
    Integer credits,
    Integer sortOrder,
    SubjectRecurrence subjectRecurrence,
    Long electiveGroupId) {}
```

### ProgramSubjectResponse — agregar category

```java
public record ProgramSubjectResponse(
    Long id, Long subjectId, String subjectName, String subjectCode,
    Integer weeklyHours,
    ProgramSubjectCategory category,
    Integer curricularYear, String curricularTerm,
    Integer credits, Integer sortOrder, String status,
    String subjectRecurrence, Long electiveGroupId) { ... }
```

(el campo `weeklyHours` viene del Subject relacionado)

### SetCatalogVisibilityRequest — nuevo DTO

```java
public record SetCatalogVisibilityRequest(@NotNull Boolean visible) {}
```

---

## Nuevos endpoints

### PUT /programs/{id}/catalog-visibility

```
PUT /programs/{id}/catalog-visibility
Authorization: DIRECTOR | ADMINISTRATIVO
Body: SetCatalogVisibilityRequest
Response: 200 ProgramResponse
```

### PUT /courses/{id}/catalog-visibility

```
PUT /courses/{id}/catalog-visibility
Authorization: DIRECTOR | ADMINISTRATIVO
Body: SetCatalogVisibilityRequest
Response: 200 CourseResponse
```

---

## Nuevos Use Cases

### SetProgramCatalogVisibilityUseCase

```java
public interface SetProgramCatalogVisibilityUseCase {
    ProgramResponse execute(Long programId, boolean visible);
}
```

### SetCourseCatalogVisibilityUseCase

Análogo al de Program.

---

## Migración V62

```sql
ALTER TABLE subjects
    ADD COLUMN weekly_hours INTEGER;

ALTER TABLE programs
    ADD COLUMN total_hours_required INTEGER,
    ADD COLUMN visible_in_catalog BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE program_subjects
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'MANDATORY';

ALTER TABLE courses
    ADD COLUMN visible_in_catalog BOOLEAN NOT NULL DEFAULT FALSE;
```

---

## Tests

**Unitarios (use cases de visibilidad):**
- `SetProgramCatalogVisibilityService`: program no encontrado → `ProgramNotFoundException`. Program encontrado → `visibleInCatalog` se actualiza y se devuelve el response.

**Integración:**
- `PUT /programs/{id}/catalog-visibility` sin rol → 403. Con rol ADMINISTRATIVO → 200.
- `POST /programs/{id}/subjects` con `category = ELECTIVE` y sin `electiveGroupId` → 400.
- `POST /programs/{id}/subjects` con `category = MANDATORY` sin `curricularYear` → 400.

---

## Verificación

1. Correr `./mvnw test` — todos los tests pasan.
2. `POST /subjects` con `weeklyHours: 4` → `GET /subjects/{id}` devuelve `weeklyHours: 4`.
3. `POST /programs/{id}/subjects` con `category: ELECTIVE, electiveGroupId: null` → 400.
4. `PUT /programs/{id}/catalog-visibility` con `{ "visible": true }` → `GET /programs/{id}` devuelve `visibleInCatalog: true`.
