import { useRef, useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { FilaAviso } from './FilaAviso';
import { RosterSegmento } from './RosterSegmento';
import { useDestinatariosDeAviso } from '../hooks/useAvisos';

const COLUMNAS = ['Aviso', 'Categoría', 'Audiencia', 'Estado', 'Creado', 'Envío', 'Vence'];

function SiluetaDeTabla() {
  return (
    <div className="flex flex-col gap-2 p-4">
      <Skeleton className="h-8" />
      <Skeleton className="h-12" />
      <Skeleton className="h-12" />
      <Skeleton className="h-12" />
    </div>
  );
}

export function TablaAvisos({ avisos, isLoading, isError, onReintentar, soloLectura, onNuevoAviso, onEditar, onEliminar, eliminando }) {
  const scrollerRef = useRef(null);
  const [aEliminar, setAEliminar] = useState(null);
  const [verDestinatariosDe, setVerDestinatariosDe] = useState(null);

  if (isLoading) {
    return (
      <GlassCard className="card-avisos overflow-hidden p-0">
        <SiluetaDeTabla />
      </GlassCard>
    );
  }

  if (isError) {
    return (
      <GlassCard className="card-avisos p-4">
        <EmptyState
          icon="⚠️"
          title="No pudimos cargar los avisos"
          description="Puede ser un problema de conexión. Probá de nuevo en un momento."
          action={
            <Button variant="outline" onClick={onReintentar}>
              Reintentar
            </Button>
          }
        />
      </GlassCard>
    );
  }

  const lista = avisos ?? [];

  if (lista.length === 0) {
    return (
      <GlassCard className="card-avisos p-4">
        <EmptyState
          icon="📣"
          title="Todavía no publicaste avisos en esta comisión"
          description="Los avisos les llegan a tus alumnos en Novedades y en su campana de notificaciones."
          action={soloLectura ? null : <Button variant="gradient" onClick={onNuevoAviso}>Nuevo aviso</Button>}
        />
      </GlassCard>
    );
  }

  async function confirmarEliminacion() {
    await onEliminar(aEliminar);
    setAEliminar(null);
  }

  return (
    <>
      <GlassCard className="card-avisos overflow-hidden p-0">
        <div className="px-4 pt-3 text-[11px] text-ink-faint">Todos los horarios en UTC−3</div>
        <div ref={scrollerRef} className="overflow-x-auto">
          <table className="tabla-avisos w-full min-w-[880px] border-collapse">
            <thead>
              <tr>
                {COLUMNAS.map((columna) => (
                  <th
                    key={columna}
                    className="whitespace-nowrap border-b border-border px-3 py-2.5 text-left text-[10.5px] font-bold uppercase tracking-[0.04em] text-ink-faint"
                  >
                    {columna}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {lista.map((aviso) => (
                <FilaAviso
                  key={aviso.id}
                  aviso={aviso}
                  scrollerRef={scrollerRef}
                  soloLectura={soloLectura}
                  onEditar={onEditar}
                  onEliminar={setAEliminar}
                  onVerDestinatarios={setVerDestinatariosDe}
                />
              ))}
            </tbody>
          </table>
        </div>
      </GlassCard>

      <RosterDeAvisoEnviado aviso={verDestinatariosDe} onClose={() => setVerDestinatariosDe(null)} />

      <Dialog open={!!aEliminar} onOpenChange={(abierto) => !abierto && setAEliminar(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Eliminar “{aEliminar?.title}”</DialogTitle>
            <DialogDescription>
              {aEliminar?.status === 'SCHEDULED'
                ? 'Todavía no se envió, así que no les va a quedar ningún rastro a tus alumnos.'
                : 'El aviso desaparece de Novedades y de la campana de quienes todavía no lo leyeron.'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" autoFocus onClick={() => setAEliminar(null)}>
              Volver
            </Button>
            <Button variant="destructive" loading={eliminando} onClick={confirmarEliminacion}>
              Eliminar aviso
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function RosterDeAvisoEnviado({ aviso, onClose }) {
  const { data: destinatarios, isLoading, isError } = useDestinatariosDeAviso(aviso?.id);
  const programado = aviso?.status === 'SCHEDULED';

  return (
    <RosterSegmento
      abierto={!!aviso}
      titulo={aviso?.audienceLabel ?? ''}
      descripcion={
        programado
          ? 'Todavía no se envió, así que no hay destinatarios: el grupo se calcula en el momento del envío.'
          : `Les llegó a ${destinatarios?.length ?? '—'} alumnos cuando se envió el aviso.`
      }
      destinatarios={destinatarios}
      isLoading={isLoading}
      isError={isError}
      onClose={onClose}
    />
  );
}
