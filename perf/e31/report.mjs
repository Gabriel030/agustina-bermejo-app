const kb = (bytes) => `${(bytes / 1024).toFixed(1)}`;
const sec = (ms) => `${(ms / 1000).toFixed(1)}`;

export function buildReport(results) {
  const rows = [];
  for (const [role, data] of Object.entries(results.roles)) {
    for (const screen of data.screens ?? []) rows.push({ role, ...screen });
  }
  const measured = rows.filter((r) => !r.redirected && r.apiCount > 0);
  const heavy = [...measured].sort((a, b) => b.apiCount - a.apiCount || b.apiBytes - a.apiBytes || a.path.localeCompare(b.path)).slice(0, 10);
  const lines = [];
  lines.push('# Épica 31 — línea base de llamadas HTTP por pantalla (31.1)', '');
  lines.push(`Generado por \`perf/e31/measure.mjs\` el ${results.generatedAt}. Red: ${results.network}; CPU ${results.cpu}; caché desactivada y contexto de navegador nuevo por pantalla (carga en frío). Solo lectura: no se ejecuta ninguna acción que modifique datos.`, '');
  lines.push('Definiciones:', '');
  lines.push('- **Peticiones**: llamadas a la API (excluye preflight CORS y estáticos); los estáticos se informan aparte.');
  lines.push('- **Duplicadas**: peticiones repetidas con mismo método y URL (cuenta las repeticiones sobrantes).');
  lines.push('- **Cascada**: nivel de dependencia temporal. Una petición está en el nivel N+1 si empezó después de que terminó otra de nivel N. Nivel 1 = lo que salió en paralelo en la primera ola; cascada = niveles >= 2.');
  lines.push('- **Contenido útil**: LCP del navegador (primer pintado del contenido principal). **Pantalla completa**: hasta que no queda ninguna llamada a la API en vuelo durante 600 ms.');
  lines.push('- Los tiempos varían entre corridas; los conteos de peticiones, duplicadas y cascadas son los comparables.', '');
  lines.push(`Pantallas medidas: ${measured.length} de ${rows.length} intentadas.`, '');
  lines.push('## 10 pantallas más pesadas', '');
  lines.push('Orden: más peticiones, luego más bytes de API.', '');
  heavy.forEach((s, i) => {
    lines.push(`### ${i + 1}. ${s.role} \`${s.path}\` — ${s.apiCount} peticiones, ${s.duplicateCount} duplicadas, cascada ${s.cascadeDepth} niveles, ${kb(s.apiBytes)} KB de API, ${sec(s.fullScreenMs)} s`, '');
    if (s.duplicates.length) {
      lines.push('Duplicadas:');
      s.duplicates.forEach((d) => lines.push(`- \`${d.request}\` ×${d.times}`));
      lines.push('');
    }
    if (s.cascades.length) {
      lines.push('En cascada (pide esperar a otra respuesta):');
      s.cascades.slice(0, 12).forEach((c) => lines.push(`- nivel ${c.level}: \`${c.request}\` (después de \`${c.after}\`)`));
      if (s.cascades.length > 12) lines.push(`- … y ${s.cascades.length - 12} más`);
      lines.push('');
    }
    if (!s.duplicates.length && !s.cascades.length) lines.push('Sin duplicadas ni cascadas: el peso viene de la cantidad o el tamaño de las respuestas.', '');
    const big = [...s.requests].sort((a, b) => b.bytes - a.bytes).slice(0, 3);
    lines.push(`Respuestas más grandes: ${big.map((b) => `\`${b.request}\` ${kb(b.bytes)} KB`).join(', ')}.`, '');
  });
  lines.push('## Todas las pantallas', '');
  lines.push('| Rol | Pantalla | Peticiones | Duplicadas | Niveles de cascada | Bytes API (KB) | Estáticos (n / KB) | Errores HTTP | Contenido útil (s) | Pantalla completa (s) | Nota |');
  lines.push('|---|---|---|---|---|---|---|---|---|---|---|');
  [...rows].sort((a, b) => a.role.localeCompare(b.role) || a.path.localeCompare(b.path)).forEach((s) => {
    const note = s.redirected ? `redirige a ${s.finalPath}` : s.loadError ? 'error de carga' : !s.networkIdle ? 'no llegó a red quieta' : '';
    lines.push(`| ${s.role} | ${s.path} | ${s.apiCount} | ${s.duplicateCount} | ${s.cascadeDepth} | ${kb(s.apiBytes)} | ${s.staticCount} / ${kb(s.staticBytes)} | ${s.errors} | ${sec(s.usefulContentMs)} | ${sec(s.fullScreenMs)} | ${note} |`);
  });
  lines.push('', '## Acciones principales (lectura segura): clic → resultado visible', '');
  lines.push('| Rol | Acción | Medida | Clic → resultado visible (s) | Peticiones | KB API |');
  lines.push('|---|---|---|---|---|---|');
  for (const [role, data] of Object.entries(results.roles)) {
    for (const a of data.actions ?? []) {
      lines.push(a.measured && a.clickToResultMs >= 45000
        ? `| ${role} | ${a.name} | no medible: tope de 45 s, queda una petición en vuelo que no termina (bug de la medición) | - | ${a.apiCount} | ${kb(a.apiBytes)} |`
        : a.measured
        ? `| ${role} | ${a.name} | sí | ${sec(a.clickToResultMs)} | ${a.apiCount} | ${kb(a.apiBytes)} |`
        : `| ${role} | ${a.name} | no: ${a.reason} | - | - | - |`);
    }
  }
  const failed = Object.entries(results.roles).filter(([, d]) => d.loginError);
  lines.push('', '## No medido', '');
  lines.push('- Pantallas con parámetros (detalles por id), asistentes de varios pasos y acciones que modifican datos.');
  lines.push('- Tiempo clic → resultado de las acciones: 6 de 7 llegaron al tope de 45 s por una petición que la medición ve en vuelo siempre; solo se informan sus peticiones y bytes. Pendiente de corregir en la próxima subtarea.');
  lines.push('- Rol Preceptor y Admin de plataforma.');
  lines.push('- Rendimiento en dispositivo real: la CPU x4 es una emulación de Chromium, no un teléfono.');
  failed.forEach(([role, d]) => lines.push(`- ${role}: login falló (${d.loginError}).`));
  rows.filter((r) => r.redirected).forEach((r) => lines.push(`- ${r.role} \`${r.path}\`: redirigió a \`${r.finalPath}\` (sin permiso o sin datos), excluida del ranking.`));
  return `${lines.join('\n')}\n`;
}
