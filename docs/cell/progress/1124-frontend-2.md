# #1124 frontend tanda 2: Alumno pagos

## Criterios
- [pasa] Elementos crudos de las seis pantallas reemplazados por componentes del proyecto (Button row y primary asChild).
- [pasa] Excepción de raw-button de AlumnoPagarSeleccion quitada; no se agregó ninguna.
- [pasa] Gradientes y literales de color fuera de las pantallas (solo tokens bg-brand, text-on-accent).
- [pasa] min-h-11 en botones de las seis pantallas.
- [no aplica] Barras fijas inferiores: ninguna de las seis pantallas tiene barra fija; StickyActionBar no se usa.
- [pendiente] Verificación visual en claro, oscuro y móvil: sin capturas en esta sesión.

## Notas
- Hecho: cambios en las seis páginas, lint, build:check, tests de las pantallas y guards en verde.
- Decisión: font-mono se mantiene en AlumnoPagarTransferencia (CBU), permitido por restyle-guards; no es un monto.
