export function CalendarLegend() {
  return (
    <ul aria-label="Leyenda" className="flex flex-wrap gap-x-4 gap-y-1 text-size-12 text-ink-soft">
      <li>
        <span aria-hidden="true" className="text-brand">●</span> Clase
      </li>
      <li>
        <span aria-hidden="true" className="text-warning-ink">◆</span> Feriado
      </li>
      <li>
        <span aria-hidden="true" className="text-success-ink">■</span> Mesa de examen
      </li>
      <li>
        <span aria-hidden="true" className="text-neutral-ink">▲</span> Vencimiento (después)
      </li>
      <li>
        <span className="line-through">Tachado</span> cancelado
      </li>
    </ul>
  );
}
