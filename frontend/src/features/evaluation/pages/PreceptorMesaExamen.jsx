import { useState } from 'react';
import { ChevronsUpDownIcon } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import { formatearFecha } from '@/lib/comisiones';
import { useMesas, useMesa, useAnotados, useInstructores, useAsignarTribunal, useCerrarActa, useDarDeBajaInscripcion } from '@/features/evaluation/hooks/useMesasDeExamen';

const ESTADO_MESA = { SCHEDULED: { label: 'Agendada', tone: 'accent' }, CLOSED: { label: 'Cerrada', tone: 'neutral' } };
const ESTADO_INSCRIPCION = {
  REGISTERED: { label: 'Anotado', tone: 'accent' },
  GRADED: { label: 'Rindió', tone: 'success' },
  WITHDRAWN: { label: 'Dado de baja', tone: 'neutral' },
};

const MENSAJES_ERROR_MESA = {
  EXAM_BOARD_NOT_YET_TAKEN: 'Todavía no se tomó el examen: el acta se cierra después de la fecha de la mesa.',
};

function iniciales(nombre) {
  return nombre.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}

function AvatarTribunal({ tribunal }) {
  if (!tribunal || tribunal.length === 0) {
    return <div className="mt-2 text-[13px] text-ink-faint">Sin asignar</div>;
  }
  const visibles = tribunal.slice(0, 2);
  const restantes = tribunal.length - visibles.length;
  return (
    <div className="mt-2 flex" title={tribunal.map((t) => t.name).join(', ')}>
      {visibles.map((t, i) => (
        <div
          key={t.id}
          className="flex h-8 w-8 items-center justify-center rounded-[10px] border-2 border-surface bg-gradient-to-br from-brand to-accent-2 text-[11px] font-bold text-white"
          style={{ marginLeft: i === 0 ? 0 : -8 }}
        >
          {iniciales(t.name)}
        </div>
      ))}
      {restantes > 0 && (
        <div className="flex h-8 w-8 items-center justify-center rounded-[10px] border-2 border-surface bg-neutral-ink text-[11px] font-bold text-white" style={{ marginLeft: -8 }}>
          +{restantes}
        </div>
      )}
    </div>
  );
}

function ModalAsignarTribunal({ open, onClose, onAsignar, asignando }) {
  const { data: instructores } = useInstructores();
  const [instructorId, setInstructorId] = useState('');

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Asignar tribunal</DialogTitle>
          <DialogDescription>Elegí un docente para sumarlo al tribunal de esta mesa.</DialogDescription>
        </DialogHeader>
        <Select value={instructorId} onValueChange={setInstructorId}>
          <SelectTrigger className="w-full"><SelectValue placeholder="Elegí un docente" /></SelectTrigger>
          <SelectContent>
            {(instructores ?? []).map((i) => <SelectItem key={i.id} value={String(i.id)}>{i.name}</SelectItem>)}
          </SelectContent>
        </Select>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cerrar</Button>
          <Button
            variant="gradient"
            disabled={!instructorId || asignando}
            onClick={() => {
              onAsignar(Number(instructorId));
              setInstructorId('');
            }}
          >
            Agregar al tribunal
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function TablaAnotados({ anotados, soloLectura, onDarDeBaja, dandoDeBaja }) {
  if (anotados.length === 0) {
    return <EmptyState title="Todavía no hay alumnos anotados" className="py-8" />;
  }
  return (
    <div className="flex flex-col gap-1">
      <div className="grid grid-cols-[2fr_1.4fr_0.6fr_0.6fr_1fr_100px] gap-2 border-b border-border px-3 pb-2 text-[11px] font-bold uppercase tracking-wide text-ink-faint">
        <div>Alumno</div><div>Carrera</div><div>Intento</div><div>Nota</div><div>Estado</div><div />
      </div>
      {anotados.map((a) => {
        const config = ESTADO_INSCRIPCION[a.status] ?? { label: a.status, tone: 'neutral' };
        return (
          <div key={a.id} className="grid grid-cols-[2fr_1.4fr_0.6fr_0.6fr_1fr_100px] items-center gap-2 rounded-xl px-3 py-2.5 hover:bg-neutral-bg/40">
            <div className="text-[13.5px] font-semibold text-ink">{a.studentName}</div>
            <div className="text-[13px] text-ink-soft">{a.carrera}</div>
            <div className="text-[13px] text-ink-soft">{a.attemptNumber ?? '-'}°</div>
            <div className="text-[13px] font-semibold text-ink">{a.finalScore ?? '—'}</div>
            <div><StatusPill tone={config.tone}>{config.label}</StatusPill></div>
            <div>
              {a.status === 'REGISTERED' && !soloLectura && (
                <button
                  type="button"
                  disabled={dandoDeBaja}
                  onClick={() => onDarDeBaja(a.id)}
                  className="text-[12.5px] font-semibold text-danger hover:underline disabled:opacity-50"
                >
                  Dar de baja
                </button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function DetalleMesa({ mesaId }) {
  const { data: mesa, isLoading: cargandoMesa } = useMesa(mesaId);
  const { data: anotados, isLoading: cargandoAnotados } = useAnotados(mesaId);
  const asignarTribunal = useAsignarTribunal(mesaId);
  const cerrarActa = useCerrarActa(mesaId);
  const darDeBaja = useDarDeBajaInscripcion(mesaId);

  const [modalTribunal, setModalTribunal] = useState(false);
  const [confirmarCierre, setConfirmarCierre] = useState(false);
  const [cerrando, setCerrando] = useState(false);

  if (cargandoMesa || cargandoAnotados || !mesa) {
    return (
      <div className="flex flex-col gap-4 pt-4">
        <Skeleton className="h-24" />
        <Skeleton className="h-40" />
      </div>
    );
  }

  const soloLectura = mesa.status === 'CLOSED';
  const materia = mesa.subjectName ?? mesa.courseSubjectName ?? 'Materia';
  const inscriptos = (anotados ?? []).filter((a) => a.status !== 'WITHDRAWN').length;
  const ahora = new Date();
  const examenPendiente = ahora < new Date(mesa.examDate);
  const ventanaAbierta = soloLectura
    ? false
    : mesa.registrationStart && mesa.registrationEnd
      ? ahora >= new Date(mesa.registrationStart) && ahora <= new Date(mesa.registrationEnd)
      : null;

  async function confirmarCerrarActa() {
    setCerrando(true);
    try {
      await cerrarActa.mutateAsync();
    } catch (e) {
      toast.error(MENSAJES_ERROR_MESA[e.code] ?? e.message);
    } finally {
      setConfirmarCierre(false);
      setCerrando(false);
    }
  }

  return (
    <div className="flex flex-col gap-4 pt-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-heading text-xl font-bold text-ink">Mesa de examen · {materia}</h2>
          <p className="mt-0.5 text-sm text-ink-soft">{formatearFecha(mesa.examDate)}</p>
        </div>
        <StatusPill tone={ESTADO_MESA[mesa.status]?.tone ?? 'neutral'}>{ESTADO_MESA[mesa.status]?.label ?? mesa.status}</StatusPill>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label="Inscriptos">
          <div className="font-heading text-2xl font-bold text-ink">{inscriptos}</div>
        </StatCard>
        <StatCard label="Tribunal">
          <AvatarTribunal tribunal={mesa.tribunal} />
        </StatCard>
        <StatCard label="Ventana de inscripción">
          <div className="font-heading text-lg font-bold text-ink">
            {ventanaAbierta == null ? '-' : ventanaAbierta ? 'Abierta' : 'Cerrada'}
          </div>
        </StatCard>
      </div>

      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="font-heading text-[16px] font-semibold text-ink">Alumnos anotados</div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={soloLectura} onClick={() => setModalTribunal(true)}>
              Asignar tribunal
            </Button>
            <Button variant="gradient" size="sm" disabled={soloLectura || cerrando || examenPendiente} onClick={() => setConfirmarCierre(true)}>
              Cerrar acta
            </Button>
          </div>
        </div>
        {!soloLectura && examenPendiente && (
          <div className="text-[12px] text-ink-faint">El acta se cierra después del examen, cuando ya se pueden cargar las notas.</div>
        )}
        <TablaAnotados
          anotados={anotados ?? []}
          soloLectura={soloLectura}
          onDarDeBaja={(id) => darDeBaja.mutate(id)}
          dandoDeBaja={darDeBaja.isPending}
        />
      </GlassCard>

      <ModalAsignarTribunal
        open={modalTribunal}
        onClose={() => setModalTribunal(false)}
        asignando={asignarTribunal.isPending}
        onAsignar={(instructorId) => asignarTribunal.mutate(instructorId)}
      />

      <Dialog open={confirmarCierre} onOpenChange={setConfirmarCierre}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>¿Cerrar el acta de esta mesa?</DialogTitle>
            <DialogDescription>
              Una vez cerrada, la mesa queda de solo lectura: no se puede volver a asignar tribunal ni modificar inscriptos.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmarCierre(false)}>Volver</Button>
            <Button variant="gradient" disabled={cerrando} onClick={confirmarCerrarActa}>Cerrar acta</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function opcionMesa(m) {
  return `${m.subjectName ?? m.courseSubjectName ?? 'Materia'} · ${formatearFecha(m.examDate)}`;
}

function BuscadorMesas({ mesas, mesaId, onElegir }) {
  const [abierto, setAbierto] = useState(false);
  const seleccionada = mesas.find((m) => String(m.id) === String(mesaId));

  return (
    <Popover open={abierto} onOpenChange={setAbierto}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="flex h-8 w-full items-center justify-between gap-2 rounded-lg border border-input bg-transparent px-2.5 text-sm text-ink sm:w-[340px]"
        >
          <span className="truncate">{seleccionada ? opcionMesa(seleccionada) : 'Buscar una mesa por materia...'}</span>
          <ChevronsUpDownIcon className="size-4 shrink-0 text-ink-faint" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-[340px] p-0" align="start">
        <Command>
          <CommandInput placeholder="Buscar por materia..." />
          <CommandList>
            <CommandEmpty>No se encontró ninguna mesa.</CommandEmpty>
            <CommandGroup>
              {mesas.map((m) => (
                <CommandItem
                  key={m.id}
                  value={m.subjectName ?? m.courseSubjectName ?? 'Materia'}
                  onSelect={() => {
                    onElegir(String(m.id));
                    setAbierto(false);
                  }}
                >
                  {opcionMesa(m)}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

export function PreceptorMesaExamen() {
  const [tab, setTab] = useState('SCHEDULED');
  const [mesaId, setMesaId] = useState(null);
  const { data: mesas, isLoading } = useMesas(tab);

  function cambiarTab(nuevoTab) {
    setTab(nuevoTab);
    setMesaId(null);
  }

  const lista = mesas ?? [];

  return (
    <PageContainer>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-heading text-2xl font-bold text-ink">Mesa de examen</h1>
        <div className="flex flex-wrap items-center gap-3">
          <Tabs value={tab} onValueChange={cambiarTab}>
            <TabsList variant="segmented">
              <TabsTrigger value="SCHEDULED">Agendadas</TabsTrigger>
              <TabsTrigger value="CLOSED">Cerradas</TabsTrigger>
            </TabsList>
          </Tabs>
          {!isLoading && lista.length > 0 && (
            <BuscadorMesas mesas={lista} mesaId={mesaId} onElegir={setMesaId} />
          )}
        </div>
      </div>

      {isLoading ? (
        <Skeleton className="h-40" />
      ) : lista.length === 0 ? (
        <GlassCard className="p-4">
          <EmptyState title={tab === 'SCHEDULED' ? 'Todavía no hay mesas agendadas' : 'Todavía no hay mesas cerradas'} />
        </GlassCard>
      ) : !mesaId ? (
        <GlassCard className="p-4">
          <EmptyState title="Elegí una mesa" description="Buscá arriba la mesa de examen sobre la que querés trabajar." />
        </GlassCard>
      ) : (
        <DetalleMesa mesaId={mesaId} />
      )}
    </PageContainer>
  );
}
