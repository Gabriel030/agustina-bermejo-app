# ADR 0004 — Value Objects `Money` y `Percentage` compartidos

> Verificado: 2026-10-31, commit `cd1ede80`.

- **Estado:** aceptado (requisito del 3-oct-2026; implementado en las Fases 18.1, 18.4 y 18.5 de la Épica 18)
- **Código:** `backend/src/main/java/com/red_escuela/backend/shared/domain/`

## Por qué

Plata y porcentajes circulaban como `BigDecimal` y `Double` sueltos, con redondeos distintos por módulo y sin moneda. Se pidió un único tipo para cada concepto en todo el repo.

## Decisión

- **Prohibido** modelar plata o porcentajes con `BigDecimal` o `Double` sueltos en código nuevo. Se usan `Money` (monto con `Currency`; `NonNegativeMoney` cuando no admite negativos), `MonetaryAdjustment` (ajuste nominal o porcentual) y `Percentage` (fracción de 0 a 1; `SignedPercentage` si admite signo). Viven en `shared/domain`, que no importa nada de ningún módulo.
- `Money` guarda `amount` con escala 2 y `RoundingMode.HALF_UP`, y falla si se combinan monedas distintas (`CurrencyMismatchException`). Persistencia JPA con columnas `amount` y `currency`.
- **Redondeo:** `HALF_UP` a 2 decimales al materializar cada línea o ajuste; un total es la suma de líneas ya redondeadas.
- **Contrato JSON:** un monto viaja como `{ "amount": "48000.00", "currency": "ARS" }` con `amount` como string (para que JavaScript no lo convierta a float); un porcentaje viaja como número en puntos de 0 a 100 en un campo cuyo nombre termina en `Percent` (`attendancePercent: 87.5`).
- **Pantalla:** la plata se muestra siempre con 2 decimales (`$ 48.000,00`) mediante un único formateador del front.

## Consecuencias

- Cada fase que cambia un contrato adapta el front en el mismo PR.
- Los Value Objects llevan tests unitarios puros, sin Spring.

## Cumplimiento

`ArchitectureRules` (`backend/src/test/java/com/red_escuela/backend/architecture/ArchitectureRules.java`, línea 96) prohíbe `BigDecimal`, `Double` y `Float` en el modelo de dinero, porcentajes y notas; el alcance exacto de la regla es el que define ese archivo.

## Falta confirmar

- Fecha exacta de la decisión: el vault la registra como "requisito 3-oct-2026" para el contrato y el plan de la Fase 18.1.
