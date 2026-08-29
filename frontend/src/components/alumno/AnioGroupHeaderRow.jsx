// Fila separadora de grupo por año curricular — reusada por TabMaterias.jsx,
// TabNotas.jsx y TabAsistencia.jsx (mismo criterio de agrupamiento en las 3).
export function AnioGroupHeaderRow({ anio, colSpan }) {
  return (
    <tr>
      <td colSpan={colSpan} className="bg-neutral-bg px-4 py-1.5 text-[11px] font-bold tracking-wide text-ink-faint uppercase">
        {anio != null ? `${anio}° año` : 'Sin año asignado'}
      </td>
    </tr>
  );
}
