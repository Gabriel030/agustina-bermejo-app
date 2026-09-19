import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, Search } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { useClassroomsAvailability } from '@/features/oferta-academica/hooks/useClassrooms';

const DIAS = [
  { id: 'MONDAY', label: 'Lunes' },
  { id: 'TUESDAY', label: 'Martes' },
  { id: 'WEDNESDAY', label: 'Miércoles' },
  { id: 'THURSDAY', label: 'Jueves' },
  { id: 'FRIDAY', label: 'Viernes' },
  { id: 'SATURDAY', label: 'Sábado' },
];

const FRANJAS = [
  { label: 'Mañana', startTime: '08:00', endTime: '12:00' },
  { label: 'Tarde', startTime: '13:00', endTime: '18:00' },
  { label: 'Noche', startTime: '18:00', endTime: '22:00' },
];

function PildoraAula({ aula }) {
  const disponible = aula.available;
  return (
    <div
      className={`rounded-xl px-3 py-2 text-[13px] ${
        disponible
          ? 'bg-success-soft text-success'
          : 'bg-danger-soft text-danger'
      }`}
    >
      <div className="font-medium">{aula.name}</div>
      <div className="text-[11px] opacity-75">{aula.capacity} lugares · {disponible ? 'Libre' : 'Ocupada'}</div>
    </div>
  );
}

function PanelDisponibilidad({ termId, dia, franja, busqueda }) {
  const { data: aulas, isLoading } = useClassroomsAvailability({
    termId,
    dayOfWeek: dia,
    startTime: franja.startTime,
    endTime: franja.endTime,
  });

  const aulasFiltradas = (aulas ?? []).filter((a) =>
    a.name.toLowerCase().includes(busqueda.toLowerCase())
  );

  if (isLoading) return <Skeleton className="h-[80px] w-full rounded-xl" />;
  if (!aulasFiltradas.length) return (
    <div className="rounded-xl border border-surface-border p-3 text-[12px] text-ink-faint">
      {aulas?.length ? 'Sin resultados para la búsqueda.' : 'Sin datos de aulas.'}
    </div>
  );

  return (
    <div className="grid grid-cols-2 gap-2 lg:grid-cols-3">
      {aulasFiltradas.map((a) => <PildoraAula key={a.id} aula={a} />)}
    </div>
  );
}

export function PreceptorCalendario({ termId }) {
  const [diaActivo, setDiaActivo] = useState('MONDAY');
  const [franjaActiva, setFranjaActiva] = useState(FRANJAS[0]);
  const [busqueda, setBusqueda] = useState('');

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to="/preceptor/oferta-academica"
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Oferta académica
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Disponibilidad de aulas</h1>
        </div>

        <GlassCard className="flex flex-col gap-4 p-4">
          <div className="flex gap-2 overflow-x-auto pb-1">
            {DIAS.map((d) => (
              <button
                key={d.id}
                type="button"
                onClick={() => setDiaActivo(d.id)}
                className={`shrink-0 rounded-full px-4 py-1.5 text-[13px] font-medium transition-colors ${
                  diaActivo === d.id ? 'bg-accent text-white' : 'bg-neutral-bg/60 text-ink-soft hover:bg-neutral-bg'
                }`}
              >
                {d.label}
              </button>
            ))}
          </div>

          <div className="flex gap-2">
            {FRANJAS.map((f) => (
              <button
                key={f.label}
                type="button"
                onClick={() => setFranjaActiva(f)}
                className={`rounded-lg px-3 py-1 text-[12px] transition-colors ${
                  franjaActiva.label === f.label
                    ? 'bg-accent-soft font-medium text-accent'
                    : 'text-ink-faint hover:text-ink'
                }`}
              >
                {f.label}
                <span className="ml-1 opacity-60">({f.startTime}–{f.endTime})</span>
              </button>
            ))}
          </div>

          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
            <Input
              placeholder="Filtrar aulas…"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              className="pl-9"
            />
          </div>

          {termId ? (
            <PanelDisponibilidad
              termId={termId}
              dia={diaActivo}
              franja={franjaActiva}
              busqueda={busqueda}
            />
          ) : (
            <EmptyState title="Seleccioná un período para ver disponibilidad." className="py-6" />
          )}
        </GlassCard>
      </div>
    </PageContainer>
  );
}
