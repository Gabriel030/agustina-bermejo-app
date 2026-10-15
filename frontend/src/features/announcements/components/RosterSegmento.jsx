import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { nombreDeInstancia } from '@/lib/avisos';

function columnasDeNotas(destinatarios, segmentKey) {
  if (!segmentKey) return [];
  const campo = segmentKey.startsWith('TP_') ? 'tpScores' : segmentKey.startsWith('FINAL_') ? 'finalScores' : 'examScores';
  const numeros = new Set();
  for (const alumno of destinatarios) {
    Object.keys(alumno[campo] ?? {}).forEach((n) => numeros.add(Number(n)));
  }
  return [...numeros].sort((a, b) => a - b).map((numero) => ({ numero, campo, label: nombreDeInstancia(segmentKey, numero) }));
}

export function RosterSegmento({ abierto, titulo, descripcion, destinatarios, segmentKey, isLoading, isError, onClose }) {
  const lista = destinatarios ?? [];
  const columnas = columnasDeNotas(lista, segmentKey);

  return (
    <Dialog open={abierto} onOpenChange={(estaAbierto) => !estaAbierto && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          {descripcion && <DialogDescription>{descripcion}</DialogDescription>}
        </DialogHeader>

        {isLoading && <Skeleton className="h-32" />}

        {isError && <EmptyState icon="⚠️" title="No pudimos cargar los destinatarios" />}

        {!isLoading && !isError && lista.length === 0 && (
          <EmptyState
            title="Todavía no hay destinatarios"
            description="El grupo se calcula en el momento del envío."
          />
        )}

        {!isLoading && !isError && lista.length > 0 && (
          <div className="max-h-80 overflow-auto rounded-xl border border-border">
            <table className="w-full border-collapse text-[12.5px]">
              <thead>
                <tr>
                  {['Legajo', 'Alumno', ...columnas.map((c) => c.label)].map((encabezado) => (
                    <th
                      key={encabezado}
                      className="sticky top-0 border-b border-border bg-surface px-2.5 py-1.5 text-left text-[10.5px] font-bold uppercase tracking-[0.04em] text-ink-faint"
                    >
                      {encabezado}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {lista.map((alumno) => (
                  <tr key={alumno.studentId} className="transition-colors hover:bg-accent-soft">
                    <td className="border-b border-border/60 px-2.5 py-1.5 font-bold tabular-nums text-ink">
                      {alumno.file ?? '—'}
                    </td>
                    <td className="border-b border-border/60 px-2.5 py-1.5 text-ink-soft">{alumno.name}</td>
                    {columnas.map((columna) => {
                      const nota = alumno[columna.campo]?.[columna.numero];
                      return (
                        <td
                          key={columna.label}
                          className={`border-b border-border/60 px-2.5 py-1.5 tabular-nums ${
                            nota === undefined ? 'font-bold text-warning' : 'text-ink-soft'
                          }`}
                        >
                          {nota === undefined ? 'sin nota' : nota}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
