# Línea base de llamadas HTTP por pantalla (Épica 31 / subtarea 31.1)

Mide, con Chromium headless, cuántas llamadas a la API hace cada pantalla, cuáles salen en paralelo o en cascada, cuáles se repiten, cuántos bytes pesan y cuánto tarda con red 4G simulada (150 ms RTT, 1,6 Mbps) y CPU x4. Solo lectura: no corrige nada ni modifica datos.

## Reproducir

1. Postgres y backend con el perfil `perf`, y el seed grande: pasos 1 a 3 de `perf/README.md` pero con `perf/seed-large.sql` en el paso 3. Con el contenedor ya usado antes, `docker compose -f perf/docker-compose.perf.yml down -v` primero (si no, Flyway rechaza las migraciones). El seed demo del primer arranque tarda unos 9 minutos por los límites de velocidad del login: esperar a `Seed demo listo` y a `/actuator/health` en UP.
2. Frontend de producción apuntando al backend de perf, servido en el origen que el backend permite por CORS (5173):

```
cd frontend && VITE_API_BASE_URL=http://localhost:8461 npm run build && npm run preview -- --port 5173 --strictPort
```

3. Instalar y medir (desde `perf/e31`, la primera vez `npx playwright install chromium`):

```
cd perf/e31 && npm install && npm run measure
```

Genera `report.md` (tablas y las 10 pantallas más pesadas) y `out/latest.json` (todas las peticiones por pantalla). Tarda unos 6 minutos.

## Variables

`E31_FRONT` (default `http://localhost:5173`), `E31_API` (`http://localhost:8461`), `E31_PASSWORD` (`Test1234`), `E31_ROLES` (lista separada por comas: `Director,Administrativo,Preceptor,Docente,Alumno,Plataforma`), `E31_OUT` (directorio del JSON), `E31_DIRECTOR`, `E31_ADMIN`, `E31_TEACHER` (`perft12`, docente del seed grande) y `E31_PRECEPTOR` (`preceptor.demo`), `E31_PLATFORM` (sin valor por defecto: no hay usuario de plataforma en `docs/context/test-users.md`; sin esta variable el rol se informa como no medido con su motivo) y `E31_STUDENT` (`alumno.demo`).

## Cómo se mide

- Por pantalla: contexto de navegador nuevo, login (sin throttling), y recién entonces red 4G y CPU x4 activos y carga en frío (caché desactivada). Cada login es nuevo porque el refresh token rota y no se puede reutilizar entre contextos.
- La carga incluye la llamada de refresco de sesión (`POST /auth/refresh`) y las de la carcasa (`/me`, módulos, avisos legales, notificaciones): son parte real de abrir una pantalla.
- Las peticiones se capturan por el protocolo DevTools (CDP). Cascada: una petición está en el nivel N+1 si empezó después de que terminó otra de nivel N.
- Contenido útil = LCP; pantalla completa = sin llamadas a la API en vuelo durante 600 ms.
- Los conteos son reproducibles entre corridas; los tiempos no, porque dependen de la carga de la máquina.

## Pantallas y acciones

Las rutas, los detalles muestreados y las acciones de lectura segura están en `screens.mjs`. `coverage.test.mjs` compara ese catálogo con las rutas de `frontend/src/features/*/routes.jsx`: una ruta nueva sin listar, o una pantalla listada que ya no existe, rompe `npm test`. Las rutas con parámetro de id se miden solo en los detalles de `details` (se llega con un clic desde la lista y se mide la URL resultante); si el seed no tiene datos o el selector no coincide, el informe lo dice en «No medido». `inicio` se mide en su destino final y se informa en la sección «Redirecciones».

## Acciones: clic → resultado

`measureAction` solo considera peticiones iniciadas después del clic (las anteriores se descartan por identificador) y trata como colgada toda petición a la API que lleve más de 10 s sin `loadingFinished` ni `loadingFailed`: no bloquea la medición y se lista en la columna «Peticiones colgadas». Si el elemento no está visible, la acción se informa como no medida con el selector probado; si llega al tope de 45 s con peticiones activas, se informa como no medible, nunca como un tiempo.

## Comparar contra la línea base

`baseline.json` guarda por pantalla `requests`, `duplicates`, `cascadeDepth` y `errors` (sin tiempos: no son reproducibles). Detalles muestreados se guardan como `<Rol> detalle <ruta>`.

```
cd perf/e31 && npm run measure && npm run compare
```

`compare.mjs` sale 0 si ninguna pantalla supera la línea base y 1 si alguna sube peticiones, duplicadas, niveles o errores, listando cuál. Las pantallas sin línea base o no medidas en la corrida (por ejemplo con `E31_ROLES` parcial) se informan sin fallar. No corre en la CI de los PR: necesita el stack local con el seed grande.

Cuando una mejora es intencional (o se suma una pantalla), correr la medición completa y `npm run compare:update`: fusiona lo medido sobre `baseline.json` y se commitea junto con el cambio que lo motiva. La línea base inicial se sembró desde el `report.md` del 17-nov, anterior a 31.2: es una cota holgada hasta la primera actualización con una corrida real.

Tests de los scripts (sin navegador ni stack): `cd perf/e31 && npm test`.
