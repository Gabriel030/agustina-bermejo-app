import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useDestinatariosDeAviso } from '../hooks/useAvisos';

export function RosterSegmento({ aviso, onClose }) {
  const { data: destinatarios, isLoading, isError } = useDestinatariosDeAviso(aviso?.id);
  const programado = aviso?.status === 'SCHEDULED';

  return (
    <Dialog open={!!aviso} onOpenChange={(abierto) => !abierto && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{aviso?.audienceLabel}</DialogTitle>
          <DialogDescription>
            {programado
              ? 'Todavía no se envió, así que no hay destinatarios: el grupo se calcula en el momento del envío.'
              : `Les llegó a ${destinatarios?.length ?? '—'} alumnos cuando se envió el aviso.`}
          </DialogDescription>
        </DialogHeader>

        {isLoading && <Skeleton className="h-32" />}

        {isError && <EmptyState icon="⚠️" title="No pudimos cargar los destinatarios" />}

        {!isLoading && !isError && (destinatarios ?? []).length === 0 && (
          <EmptyState
            title={programado ? 'Se calculan al enviarse' : 'Este aviso no le llegó a nadie'}
            description={
              programado
                ? 'Cuando salga, acá vas a ver exactamente a quiénes les llegó.'
                : 'El segmento quedó vacío en el momento del envío.'
            }
          />
        )}

        {!isLoading && !isError && (destinatarios ?? []).length > 0 && (
          <div className="max-h-80 overflow-y-auto rounded-xl border border-border">
            <table className="w-full border-collapse text-[12.5px]">
              <thead>
                <tr>
                  <th className="sticky top-0 border-b border-border bg-surface px-2.5 py-1.5 text-left text-[10.5px] font-bold uppercase tracking-[0.04em] text-ink-faint">
                    Legajo
                  </th>
                  <th className="sticky top-0 border-b border-border bg-surface px-2.5 py-1.5 text-left text-[10.5px] font-bold uppercase tracking-[0.04em] text-ink-faint">
                    Alumno
                  </th>
                </tr>
              </thead>
              <tbody>
                {destinatarios.map((alumno) => (
                  <tr key={alumno.studentId} className="transition-colors hover:bg-accent-soft">
                    <td className="border-b border-border/60 px-2.5 py-1.5 font-bold tabular-nums text-ink">
                      {alumno.file ?? '—'}
                    </td>
                    <td className="border-b border-border/60 px-2.5 py-1.5 text-ink-soft">{alumno.name}</td>
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
